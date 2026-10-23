package io.digisic.bank.audit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.digisic.bank.model.Account;
import io.digisic.bank.model.security.Users;

/*
 * Structured audit trail for money movements, authentication and administrative actions.
 *
 * Events are written at INFO to the dedicated "AUDIT" logger as key=value pairs so they can be
 * routed to a separate appender and are never suppressed by application debug settings.
 * SUCCESS events raised inside a transaction are written only once it commits; a rollback is
 * recorded as FAILURE with reason=TRANSACTION_ROLLED_BACK.
 */
public final class AuditLog {

	public static final String LOGGER_NAME = "AUDIT";

	public static final String CAT_MONEY_MOVEMENT = "MONEY_MOVEMENT";
	public static final String CAT_AUTHENTICATION = "AUTHENTICATION";
	public static final String CAT_ADMIN = "ADMIN";
	public static final String CAT_SELF_SERVICE = "SELF_SERVICE";

	public static final String EVT_DEPOSIT = "DEPOSIT";
	public static final String EVT_WITHDRAWAL = "WITHDRAWAL";
	public static final String EVT_TRANSFER = "TRANSFER";
	public static final String EVT_OVERDRAFT_FEE = "OVERDRAFT_FEE";
	public static final String EVT_LOGIN_SUCCESS = "LOGIN_SUCCESS";
	public static final String EVT_LOGIN_FAILURE = "LOGIN_FAILURE";

	public static final String OUTCOME_SUCCESS = "SUCCESS";
	public static final String OUTCOME_FAILURE = "FAILURE";

	public static final String SYSTEM_ACTOR = "system";
	public static final String ANONYMOUS_ACTOR = "anonymous";
	public static final String REASON_ROLLED_BACK = "TRANSACTION_ROLLED_BACK";

	private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);

	private AuditLog() {
	}

	public static void moneyMovement(String event, Account fromAccount, Account toAccount, BigDecimal amount,
			String transactionType, String outcome, String reason) {

		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("fromAccountId", accountId(fromAccount));
		fields.put("fromAccountNumber", accountNumber(fromAccount));
		fields.put("toAccountId", accountId(toAccount));
		fields.put("toAccountNumber", accountNumber(toAccount));
		fields.put("accountOwner", ownerOf(fromAccount != null ? fromAccount : toAccount));
		fields.put("amount", amount == null ? null : amount.abs().toPlainString());
		fields.put("txnType", transactionType);
		fields.put("reason", reason);

		emit(CAT_MONEY_MOVEMENT, event, currentActor(), outcome, fields);
	}

	public static void login(String username, boolean success, String channel, String reason) {

		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("channel", channel);
		fields.put("reason", reason);

		write(CAT_AUTHENTICATION, success ? EVT_LOGIN_SUCCESS : EVT_LOGIN_FAILURE, username,
				success ? OUTCOME_SUCCESS : OUTCOME_FAILURE, fields);
	}

	public static void adminAction(String event, Users targetUser, String detail, String outcome) {
		userAction(event, targetUser, detail, outcome, false);
	}

	/*
	 * For operations only administrators can perform: always category=ADMIN, even when acting on themselves.
	 */
	public static void privilegedAdminAction(String event, Users targetUser, String detail, String outcome) {
		userAction(event, targetUser, detail, outcome, true);
	}

	private static void userAction(String event, Users targetUser, String detail, String outcome,
			boolean adminOnly) {

		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("targetUser", targetUser == null ? null : targetUser.getUsername());
		fields.put("targetUserId", targetUser == null ? null : targetUser.getId());
		fields.put("detail", detail);

		String actor = currentActor();
		boolean selfService = !adminOnly && (ANONYMOUS_ACTOR.equals(actor)
				|| (targetUser != null && actor.equals(targetUser.getUsername())));

		emit(selfService ? CAT_SELF_SERVICE : CAT_ADMIN, event, actor, outcome, fields);
	}

	public static String currentActor() {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth instanceof AnonymousAuthenticationToken) {
			return ANONYMOUS_ACTOR;
		}

		if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
			return SYSTEM_ACTOR;
		}

		return auth.getName();
	}

	private static void emit(final String category, final String event, final String actor, String outcome,
			final Map<String, Object> fields) {

		if (!OUTCOME_SUCCESS.equals(outcome) || !TransactionSynchronizationManager.isSynchronizationActive()) {
			write(category, event, actor, outcome, fields);
			return;
		}

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
			@Override
			public void afterCompletion(int status) {
				if (status == TransactionSynchronization.STATUS_COMMITTED) {
					write(category, event, actor, OUTCOME_SUCCESS, fields);
				} else {
					fields.put("reason", REASON_ROLLED_BACK);
					write(category, event, actor, OUTCOME_FAILURE, fields);
				}
			}
		});
	}

	private static void write(String category, String event, String actor, String outcome,
			Map<String, Object> fields) {

		StringBuilder sb = new StringBuilder();
		append(sb, "category", category);
		append(sb, "event", event);
		append(sb, "actor", actor);
		append(sb, "outcome", outcome);

		for (Map.Entry<String, Object> field : fields.entrySet()) {
			if (field.getValue() != null) {
				append(sb, field.getKey(), field.getValue());
			}
		}

		append(sb, "timestamp", Instant.now().toString());

		AUDIT.info(sb.toString());
	}

	private static void append(StringBuilder sb, String key, Object value) {

		if (sb.length() > 0) {
			sb.append(' ');
		}
		sb.append(key).append('=').append(sanitize(String.valueOf(value)));
	}

	/*
	 * Neutralise CR/LF (log forging) and quote values containing separators, quotes or backslashes.
	 */
	private static String sanitize(String value) {

		String clean = value.replaceAll("[\\r\\n\\t]", "_");

		if (clean.isEmpty() || clean.matches(".*[\\s=\"\\\\].*")) {
			return "\"" + clean.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		}

		return clean;
	}

	private static Long accountId(Account account) {
		return account == null ? null : account.getId();
	}

	private static Long accountNumber(Account account) {

		try {
			return account == null ? null : account.getAccountNumber();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String ownerOf(Account account) {
		return account == null || account.getOwner() == null ? null : account.getOwner().getUsername();
	}
}
