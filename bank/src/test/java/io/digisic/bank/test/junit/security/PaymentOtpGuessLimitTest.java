package io.digisic.bank.test.junit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.digisic.bank.model.UserProfile;
import io.digisic.bank.model.security.Role;
import io.digisic.bank.model.security.Users;
import io.digisic.bank.security.sca.PaymentOtpService;
import io.digisic.bank.service.UserService;

/*
 * EM-136: the payment OTP guess budget spans challenges, so asking for a fresh code does not reset it.
 */
@SpringBootTest
@ExtendWith(SpringExtension.class)
public class PaymentOtpGuessLimitTest {

	private static final String PASSWORD = "Passw0rd!";
	private static final Pattern OTP_PATTERN = Pattern.compile("code (\\d{6}) authorises");

	@Autowired
	private UserService userService;

	@Autowired
	private PaymentOtpService paymentOtpService;

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	@BeforeEach
	public void captureLogs() {
		logs.start();
		rootLogger().addAppender(logs);
	}

	@AfterEach
	public void releaseLogs() {
		rootLogger().detachAppender(logs);
	}

	@Test
	public void wrongPaymentOtpsAcrossFreshChallengesLockTheUser() {

		String username = createUser();
		Users user = userService.findByUsername(username);
		BigDecimal amount = new BigDecimal("1.00");

		for (int i = 0; i < 5; i++) {
			PaymentOtpService.Challenge c = paymentOtpService.issue(user, PaymentOtpService.OP_WITHDRAW, 1L,
					PaymentOtpService.PAYEE_CASH, amount, "test");
			String wrong = String.format("%06d", (Integer.parseInt(lastOtp()) + 1) % 1000000);
			assertFalse(paymentOtpService.verify(c.getId(), wrong, username, PaymentOtpService.OP_WITHDRAW, 1L,
					PaymentOtpService.PAYEE_CASH, amount));
		}

		assertFalse(userService.findByUsername(username).isAccountNonLocked(), "requesting new challenges must not reset the guess budget");
		assertTrue(logged("event=ACCOUNT_LOCKED user=" + username + " reason=max_failed_payment_otps"));
		assertThrows(LockedException.class, () -> paymentOtpService.issue(user, PaymentOtpService.OP_WITHDRAW, 1L,
				PaymentOtpService.PAYEE_CASH, amount, "test"));
	}

	@Test
	public void adminUnlockClearsPendingPaymentOtpFailures() {

		String username = createUser();
		Users user = userService.findByUsername(username);

		for (int i = 0; i < 4; i++) {
			failOnce(user);
		}
		userService.unlockUser(userService.findByUsername(username), true);
		failOnce(user);

		assertTrue(userService.findByUsername(username).isAccountNonLocked(), "unlock must reset the payment OTP failure budget");
	}

	@Test
	public void concurrentUseOfOneCodeAuthorisesExactlyOnePayment() throws Exception {

		String username = createUser();
		Users user = userService.findByUsername(username);
		BigDecimal amount = new BigDecimal("5.00");
		PaymentOtpService.Challenge c = paymentOtpService.issue(user, PaymentOtpService.OP_WITHDRAW, 1L,
				PaymentOtpService.PAYEE_CASH, amount, "test");
		String otp = lastOtp();

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			results.add(pool.submit(() -> {
				start.await();
				return paymentOtpService.verify(c.getId(), otp, username, PaymentOtpService.OP_WITHDRAW, 1L,
						PaymentOtpService.PAYEE_CASH, amount);
			}));
		}
		start.countDown();

		int accepted = 0;
		for (Future<Boolean> f : results) {
			accepted += f.get() ? 1 : 0;
		}
		pool.shutdown();

		assertEquals(1, accepted, "a single-use code must authorise exactly one payment");
	}

	private void failOnce(Users user) {
		BigDecimal amount = new BigDecimal("1.00");
		PaymentOtpService.Challenge c = paymentOtpService.issue(user, PaymentOtpService.OP_WITHDRAW, 1L,
				PaymentOtpService.PAYEE_CASH, amount, "test");
		String wrong = String.format("%06d", (Integer.parseInt(lastOtp()) + 1) % 1000000);
		assertFalse(paymentOtpService.verify(c.getId(), wrong, user.getUsername(), PaymentOtpService.OP_WITHDRAW, 1L,
				PaymentOtpService.PAYEE_CASH, amount));
	}

	private String createUser() {

		String email = "sca-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.io";
		Users user = new Users(email, PASSWORD);
		UserProfile profile = new UserProfile();
		profile.setEmailAddress(email);
		profile.setFirstName("Sca");
		profile.setLastName("Tester");
		profile.setTitle("Mr.");
		profile.setGender("M");
		profile.setDob(new java.util.Date(0));
		profile.setSsn(String.format("%03d-%02d-%04d", (int) (Math.random() * 900) + 100,
				(int) (Math.random() * 90) + 10, (int) (Math.random() * 9000) + 1000));
		profile.setAddress("1 Test Street");
		profile.setCountry("US");
		profile.setLocality("Testville");
		profile.setPostalCode("12345");
		profile.setRegion("CA");
		profile.setHomePhone("555-555-5555");
		profile.setMobilePhone("555-555-1234");
		profile.setWorkPhone("555-555-0000");
		user.setUserProfile(profile);
		userService.createUser(user, Role.ROLE_USER);
		return email;
	}

	private boolean logged(String fragment) {
		return logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains(fragment));
	}

	private String lastOtp() {
		String otp = null;
		for (ILoggingEvent e : logs.list) {
			Matcher m = OTP_PATTERN.matcher(e.getFormattedMessage());
			if (m.find()) {
				otp = m.group(1);
			}
		}
		assertNotNull(otp, "an OTP must have been sent out of band");
		return otp;
	}

	private static Logger rootLogger() {
		return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
	}
}
