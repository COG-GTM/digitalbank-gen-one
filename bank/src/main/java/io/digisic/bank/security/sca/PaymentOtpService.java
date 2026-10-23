package io.digisic.bank.security.sca;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.digisic.bank.model.security.Users;

/*
 * Strong customer authentication step-up for payment initiation (PSRs 2017 reg. 100, SCA-RTS Art. 5).
 * Each challenge carries a random key; the passcode is an HMAC over user, operation, source account,
 * payee and amount, so a code only authorises the exact transaction it was issued for (dynamic linking).
 */
@Service
public class PaymentOtpService {

	private static final Logger LOG = LoggerFactory.getLogger(PaymentOtpService.class);
	private static final Logger AUDIT = LoggerFactory.getLogger("io.digisic.bank.audit");

	public static final String OP_WITHDRAW = "WITHDRAW";
	public static final String OP_TRANSFER = "TRANSFER";
	public static final String PAYEE_CASH = "CASH";

	private static final String HMAC_ALG = "HmacSHA256";
	private static final int CODE_DIGITS = 6;

	private final SecureRandom random = new SecureRandom();
	private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

	@Autowired
	private OtpSender otpSender;

	@Value("${io.digisic.bank.sca.otp.ttl-seconds:300}")
	private long ttlSeconds;

	@Value("${io.digisic.bank.sca.otp.max-attempts:3}")
	private int maxAttempts;

	private Clock clock = Clock.systemUTC();

	public static String payeeAccount(Long accountId) {
		return "ACCOUNT:" + accountId;
	}

	/*
	 * Create a challenge bound to the transaction details and send its passcode out of band.
	 */
	public Challenge issue(Users user, String operation, Long fromAccountId, String payee, BigDecimal amount, String description) {

		purgeExpired();

		byte[] key = new byte[32];
		random.nextBytes(key);

		Challenge challenge = new Challenge(UUID.randomUUID().toString(), user.getUsername(), key,
				Instant.now(clock).plusSeconds(ttlSeconds));
		challenges.put(challenge.getId(), challenge);

		otpSender.send(user, computeCode(key, binding(user.getUsername(), operation, fromAccountId, payee, amount)), description);

		AUDIT.info("event=PAYMENT_OTP_ISSUED user={} operation={} fromAccount={} payee={} amount={} challenge={}",
				user.getUsername(), operation, fromAccountId, payee, normalise(amount), challenge.getId());

		return challenge;
	}

	/*
	 * Verify and consume a passcode. Returns true only if the code was issued to this user for exactly
	 * this operation, source account, payee and amount, and has not expired or been used.
	 */
	public boolean verify(String challengeId, String otp, String username, String operation, Long fromAccountId,
			String payee, BigDecimal amount) {

		if (challengeId == null || otp == null) {
			return false;
		}

		Challenge challenge = challenges.get(challengeId);

		if (challenge == null || !challenge.getUsername().equals(username)) {
			AUDIT.warn("event=PAYMENT_OTP_REJECTED user={} operation={} challenge={} reason=unknown", username, operation, challengeId);
			return false;
		}

		if (Instant.now(clock).isAfter(challenge.getExpiresAt())) {
			challenges.remove(challengeId);
			AUDIT.warn("event=PAYMENT_OTP_REJECTED user={} operation={} challenge={} reason=expired", username, operation, challengeId);
			return false;
		}

		String expected = computeCode(challenge.key, binding(username, operation, fromAccountId, payee, amount));
		boolean match = MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), otp.trim().getBytes(StandardCharsets.US_ASCII));

		if (match) {
			challenges.remove(challengeId);
			AUDIT.info("event=PAYMENT_OTP_VERIFIED user={} operation={} fromAccount={} payee={} amount={} challenge={}",
					username, operation, fromAccountId, payee, normalise(amount), challengeId);
			return true;
		}

		if (challenge.attempts.incrementAndGet() >= maxAttempts) {
			challenges.remove(challengeId);
		}

		AUDIT.warn("event=PAYMENT_OTP_REJECTED user={} operation={} fromAccount={} payee={} amount={} challenge={} reason=mismatch",
				username, operation, fromAccountId, payee, normalise(amount), challengeId);
		return false;
	}

	public boolean isActive(String challengeId, String username) {
		Challenge challenge = challengeId == null ? null : challenges.get(challengeId);
		return challenge != null && challenge.getUsername().equals(username)
				&& !Instant.now(clock).isAfter(challenge.getExpiresAt());
	}

	void setClock(Clock clock) {
		this.clock = clock;
	}

	private void purgeExpired() {
		Instant now = Instant.now(clock);
		challenges.values().removeIf(c -> now.isAfter(c.getExpiresAt()));
	}

	private static String binding(String username, String operation, Long fromAccountId, String payee, BigDecimal amount) {
		return String.join("|", username, operation, String.valueOf(fromAccountId), String.valueOf(payee), normalise(amount));
	}

	private static String normalise(BigDecimal amount) {
		return amount == null ? "null" : amount.stripTrailingZeros().toPlainString();
	}

	private static String computeCode(byte[] key, String data) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALG);
			mac.init(new SecretKeySpec(key, HMAC_ALG));
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

			// RFC 4226 dynamic truncation
			int offset = hash[hash.length - 1] & 0x0f;
			int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
					| ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);

			return String.format("%0" + CODE_DIGITS + "d", binary % (int) Math.pow(10, CODE_DIGITS));
		} catch (Exception e) {
			throw new IllegalStateException("Unable to compute one-time passcode", e);
		}
	}

	public static class Challenge {

		private final String id;
		private final String username;
		private final byte[] key;
		private final Instant expiresAt;
		private final java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();

		Challenge(String id, String username, byte[] key, Instant expiresAt) {
			this.id = id;
			this.username = username;
			this.key = key;
			this.expiresAt = expiresAt;
		}

		public String getId() {
			return id;
		}

		public String getUsername() {
			return username;
		}

		public Instant getExpiresAt() {
			return expiresAt;
		}
	}
}
