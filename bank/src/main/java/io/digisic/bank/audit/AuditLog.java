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

import io.digisic.bank.model.Account;
import io.digisic.bank.model.security.Users;

/*
 * Structured audit trail for money movements, authentication and administrative actions.
 *
 * Events are written at INFO to the dedicated "AUDIT" logger as key=value pairs so they can be
 * routed to a separate appender and are never suppressed by application debug settings.
 */
public final class AuditLog {

	public static final String LOGGER_NAME = "AUDIT";

	public static final String CAT_MONEY_MOVEMENT = "MONEY_MOVEMENT";
	public static final String CAT_AUTHENTICATION = "AUTHENTICATION";
	public static final String CAT_ADMIN = "ADMIN";

	public static final String EVT_DEPOSIT = "DEPOSIT";
	public static final String EVT_WITHDRAWAL = "WITHDRAWAL";
	public static final String EVT_TRANSFER = "TRANSFER";
	public static final String EVT_OVERDRAFT_FEE = "OVERDRAFT_FEE";
	public static final String EVT_LOGIN_SUCCESS = "LOGIN_SUCCESS";
	public static final String EVT_LOGIN_FAILURE = "LOGIN_FAILURE";

	public static final String OUTCOME_SUCCESS = "SUCCESS";
	public static final String OUTCOME_FAILURE = "FAILURE";

	public static final String SYSTEM_ACTOR = "system";

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

		write(CAT_MONEY_MOVEMENT, event, currentActor(), outcome, fields);
	}

	public static void login(String username, boolean success, String channel, String reason) {

		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("channel", channel);
		fields.put("reason", reason);

		write(CAT_AUTHENTICATION, success ? EVT_LOGIN_SUCCESS : EVT_LOGIN_FAILURE, username,
				success ? OUTCOME_SUCCESS : OUTCOME_FAILURE, fields);
	}

	public static void adminAction(String event, Users targetUser, String detail, String outcome) {

		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("targetUser", targetUser == null ? null : targetUser.getUsername());
		fields.put("targetUserId", targetUser == null ? null : targetUser.getId());
		fields.put("detail", detail);

		write(CAT_ADMIN, event, currentActor(), outcome, fields);
	}

	public static String currentActor() {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
				|| auth.getName() == null) {
			return SYSTEM_ACTOR;
		}

		return auth.getName();
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
	 * Neutralise CR/LF (log forging) and quote values containing separators.
	 */
	private static String sanitize(String value) {

		String clean = value.replaceAll("[\\r\\n\\t]", "_");

		if (clean.isEmpty() || clean.matches(".*[\\s=\"].*")) {
			return "\"" + clean.replace("\"", "\\\"") + "\"";
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
