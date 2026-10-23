package io.digisic.bank.test.junit.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.digisic.bank.audit.AuditLog;
import io.digisic.bank.audit.LoginAuditListener;
import io.digisic.bank.model.security.Users;
import io.digisic.bank.repository.AccountRepository;
import io.digisic.bank.repository.UserRepository;
import io.digisic.bank.service.AccountService;
import io.digisic.bank.service.UserService;

/**
 * EM-139 / ITGC-LOG-11: logins and administrator actions must emit "AUDIT" events.
 */
public class AuthenticationAndAdminAuditTest {

	private static final String ADMIN = "admin@demo.io";

	@Mock
	private UserRepository userRepository;

	@Mock
	private AccountRepository accountRepository;

	@InjectMocks
	private UserService userService;

	@InjectMocks
	private AccountService accountService;

	private final LoginAuditListener listener = new LoginAuditListener();

	private Logger auditLogger;
	private ListAppender<ILoggingEvent> appender;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
		auditLogger.setLevel(Level.INFO);
		appender = new ListAppender<>();
		appender.start();
		auditLogger.addAppender(appender);
	}

	@AfterEach
	public void tearDown() {
		auditLogger.detachAppender(appender);
		SecurityContextHolder.clearContext();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	public void loginSuccessAndFailureAreAudited() {

		listener.onSuccess(new AuthenticationSuccessEvent(new UsernamePasswordAuthenticationToken("jsmith@demo.io",
				null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))));
		listener.onFailure(new AuthenticationFailureBadCredentialsEvent(
				new UsernamePasswordAuthenticationToken("jsmith@demo.io\nforged=1", "wrong"),
				new BadCredentialsException("Bad credentials")));

		String success = single("event=LOGIN_SUCCESS");
		assertTrue(success.contains("category=AUTHENTICATION"), success);
		assertTrue(success.contains("actor=jsmith@demo.io"), success);
		assertTrue(success.contains("outcome=SUCCESS"), success);

		String failure = single("event=LOGIN_FAILURE");
		assertTrue(failure.contains("outcome=FAILURE"), failure);
		assertTrue(failure.contains("reason=BadCredentialsException"), failure);
		assertTrue(!failure.contains("\n"), "CR/LF must be neutralised: " + failure);
	}

	@Test
	public void adminActionsAreAuditedWithActorAndTarget() {

		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ADMIN, null,
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))));

		Users customer = new Users("jsmith@demo.io", "secret");

		userService.unlockUser(customer, true);
		accountService.deleteAllAccounts(customer);

		String unlock = single("event=USER_UNLOCK");
		assertTrue(unlock.contains("category=ADMIN"), unlock);
		assertTrue(unlock.contains("actor=" + ADMIN), unlock);
		assertTrue(unlock.contains("targetUser=jsmith@demo.io"), unlock);
		assertTrue(unlock.contains("detail=\"unlock=true\""), unlock);

		String delete = single("event=DELETE_ALL_ACCOUNTS");
		assertTrue(delete.contains("actor=" + ADMIN), delete);
		assertTrue(delete.contains("targetUser=jsmith@demo.io"), delete);
		assertTrue(delete.contains("outcome=SUCCESS"), delete);
	}

	@Test
	public void customerActingOnOwnUserIsAuditedAsSelfServiceNotAdmin() {

		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("jsmith@demo.io",
				null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))));

		accountService.deleteAllAccounts(new Users("jsmith@demo.io", "secret"));

		String delete = single("event=DELETE_ALL_ACCOUNTS");
		assertTrue(delete.contains("category=SELF_SERVICE"), delete);
		assertTrue(delete.contains("actor=jsmith@demo.io"), delete);
	}

	@Test
	public void trailingBackslashCannotSwallowFollowingFields() {

		listener.onFailure(new AuthenticationFailureBadCredentialsEvent(
				new UsernamePasswordAuthenticationToken("evil \\", "wrong"),
				new BadCredentialsException("Bad credentials")));

		String failure = single("event=LOGIN_FAILURE");
		assertTrue(failure.contains("actor=\"evil \\\\\" outcome=FAILURE"), failure);
	}

	@Test
	public void successEventsInsideTransactionAreWrittenOnlyOnCommit() {

		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ADMIN, null,
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		Users customer = new Users("jsmith@demo.io", "secret");

		TransactionSynchronizationManager.initSynchronization();
		userService.unlockUser(customer, true);
		assertTrue(appender.list.isEmpty(), "SUCCESS must wait for commit: " + appender.list);
		complete(TransactionSynchronization.STATUS_ROLLED_BACK);

		String rolledBack = single("event=USER_UNLOCK");
		assertTrue(rolledBack.contains("outcome=FAILURE"), rolledBack);
		assertTrue(rolledBack.contains("reason=" + AuditLog.REASON_ROLLED_BACK), rolledBack);

		appender.list.clear();
		TransactionSynchronizationManager.initSynchronization();
		userService.unlockUser(customer, true);
		complete(TransactionSynchronization.STATUS_COMMITTED);
		assertTrue(single("event=USER_UNLOCK").contains("outcome=SUCCESS"));
	}

	private void complete(int status) {
		List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
		TransactionSynchronizationManager.clearSynchronization();
		syncs.forEach(sync -> sync.afterCompletion(status));
	}

	private String single(String filter) {
		List<String> matches = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
				.filter(m -> m.contains(filter)).collect(Collectors.toList());
		assertEquals(1, matches.size(), "expected one " + filter + " event, got: " + matches);
		return matches.get(0);
	}
}
