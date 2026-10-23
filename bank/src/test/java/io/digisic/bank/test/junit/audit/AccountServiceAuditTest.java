package io.digisic.bank.test.junit.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.digisic.bank.model.Account;
import io.digisic.bank.model.AccountTransaction;
import io.digisic.bank.model.AccountType;
import io.digisic.bank.model.TransactionCategory;
import io.digisic.bank.model.TransactionState;
import io.digisic.bank.model.TransactionType;
import io.digisic.bank.model.security.Users;
import io.digisic.bank.repository.AccountRepository;
import io.digisic.bank.repository.TransactionCategoryRepository;
import io.digisic.bank.repository.TransactionStateRepository;
import io.digisic.bank.repository.TransactionTypeRepository;
import io.digisic.bank.service.AccountService;

/**
 * EM-139 / ITGC-LOG-11: money movements must emit INFO-level events on the "AUDIT" logger.
 */
public class AccountServiceAuditTest {

	private static final String ACTOR = "jsmith@demo.io";

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private TransactionTypeRepository transactionTypeRepository;

	@Mock
	private TransactionStateRepository transactionStateRepository;

	@Mock
	private TransactionCategoryRepository transactionCategoryRepository;

	@InjectMocks
	private AccountService accountService;

	private Logger auditLogger;
	private ListAppender<ILoggingEvent> appender;

	private Account checking;
	private Account savings;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.initMocks(this);

		auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
		auditLogger.setLevel(Level.INFO);
		appender = new ListAppender<>();
		appender.start();
		auditLogger.addAppender(appender);

		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ACTOR, null,
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))));

		Users owner = new Users(ACTOR, "secret");

		AccountType type = new AccountType();
		type.setOverdraftFee(new BigDecimal("25.00"));

		checking = account(101L, owner, type, "500.00");
		savings = account(202L, owner, type, "100.00");

		when(accountRepository.findById(101L)).thenReturn(Optional.of(checking));
		when(accountRepository.findById(202L)).thenReturn(Optional.of(savings));
		when(transactionTypeRepository.findByCode(anyString())).thenAnswer(inv -> {
			TransactionType t = new TransactionType();
			t.setCode(inv.getArgument(0));
			return t;
		});
		when(transactionStateRepository.findByCode(anyString())).thenReturn(new TransactionState());
		when(transactionCategoryRepository.findByCode(anyString())).thenReturn(new TransactionCategory());
	}

	@AfterEach
	public void tearDown() {
		auditLogger.detachAppender(appender);
		SecurityContextHolder.clearContext();
	}

	@Test
	public void transferEmitsInfoAuditEventWithWhoWhatWhenAmountAndOutcome() {

		AccountTransaction txn = new AccountTransaction();
		txn.setAmount(new BigDecimal("150.00"));
		txn.setTransactionDate(new Date());

		accountService.transfer(checking, savings, txn);

		List<String> transfers = auditMessages("event=TRANSFER");
		assertEquals(1, transfers.size(), "expected exactly one TRANSFER audit event, got: " + auditMessages(""));

		String event = transfers.get(0);
		assertTrue(event.contains("category=MONEY_MOVEMENT"), event);
		assertTrue(event.contains("actor=" + ACTOR), event);
		assertTrue(event.contains("fromAccountId=101"), event);
		assertTrue(event.contains("toAccountId=202"), event);
		assertTrue(event.contains("amount=150.00"), event);
		assertTrue(event.contains("outcome=SUCCESS"), event);
		assertTrue(event.matches(".*timestamp=\\d{4}-\\d{2}-\\d{2}T.*"), event);

		assertEquals(1, auditMessages("event=WITHDRAWAL").size(), "debit leg not audited");
		assertEquals(1, auditMessages("event=DEPOSIT").size(), "credit leg not audited");

		for (ILoggingEvent e : appender.list) {
			assertEquals(Level.INFO, e.getLevel());
			assertEquals("AUDIT", e.getLoggerName());
		}
	}

	@Test
	public void overdraftDebitEmitsWithdrawalAndOverdraftFeeAuditEvents() {

		AccountTransaction txn = new AccountTransaction();
		txn.setAmount(new BigDecimal("600.00"));
		txn.setTransactionDate(new Date());

		accountService.debitTransaction(checking, txn);

		List<String> withdrawals = auditMessages("event=WITHDRAWAL");
		assertEquals(1, withdrawals.size());
		assertTrue(withdrawals.get(0).contains("amount=600.00"), withdrawals.get(0));
		assertTrue(withdrawals.get(0).contains("fromAccountId=101"), withdrawals.get(0));

		List<String> fees = auditMessages("event=OVERDRAFT_FEE");
		assertEquals(1, fees.size());
		assertTrue(fees.get(0).contains("amount=25.00"), fees.get(0));
		assertTrue(fees.get(0).contains("outcome=SUCCESS"), fees.get(0));
	}

	@Test
	public void failedTransferIsAuditedWithFailureOutcome() {

		when(accountRepository.save(checking)).thenThrow(new IllegalStateException("db down"));

		AccountTransaction txn = new AccountTransaction();
		txn.setAmount(new BigDecimal("10.00"));
		txn.setTransactionDate(new Date());

		try {
			accountService.transfer(checking, savings, txn);
		} catch (IllegalStateException expected) {
			// expected
		}

		List<String> transfers = auditMessages("event=TRANSFER");
		assertEquals(1, transfers.size());
		assertTrue(transfers.get(0).contains("outcome=FAILURE"), transfers.get(0));
		assertTrue(transfers.get(0).contains("reason=IllegalStateException"), transfers.get(0));
	}

	private List<String> auditMessages(String filter) {
		return appender.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.contains(filter))
				.collect(Collectors.toList());
	}

	private static Account account(Long id, Users owner, AccountType type, String balance) {
		Account a = new Account();
		a.setId(id);
		a.setOwner(owner);
		a.setAccountType(type);
		a.setCurrentBalance(new BigDecimal(balance));
		a.setAcountTransactionList(new ArrayList<>());
		return a;
	}
}
