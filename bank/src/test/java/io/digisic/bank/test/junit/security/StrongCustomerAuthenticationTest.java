package io.digisic.bank.test.junit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.digisic.bank.model.Account;
import io.digisic.bank.model.UserProfile;
import io.digisic.bank.model.security.Role;
import io.digisic.bank.model.security.Users;
import io.digisic.bank.service.AccountService;
import io.digisic.bank.service.SampleDataService;
import io.digisic.bank.service.UserService;

/*
 * EM-136 / APP-SCA-13: payment step-up OTP with dynamic linking, 5-strike lock-out, remember-me hardening.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
public class StrongCustomerAuthenticationTest {

	private static final String PASSWORD = "Passw0rd!";
	private static final Pattern OTP_PATTERN = Pattern.compile("code (\\d{6}) authorises");
	private static final String LEGACY_REMEMBER_ME_KEY = "remeber-me";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService userService;

	@Autowired
	private AccountService accountService;

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

	// ---------------------------------------------------------------- lock-out

	@Test
	public void fiveConsecutiveFailedLoginsLockTheUserAndAreAuditLogged() throws Exception {

		String username = createUser();

		for (int i = 1; i <= 4; i++) {
			mvc.perform(formLogin("/login").user(username).password("wrong-" + i));
		}
		assertTrue(userService.findByUsername(username).isAccountNonLocked(), "4 failures must not lock");

		mvc.perform(formLogin("/login").user(username).password("wrong-5"));
		assertFalse(userService.findByUsername(username).isAccountNonLocked(), "5th consecutive failure must lock");

		assertTrue(logged("event=ACCOUNT_LOCKED user=" + username), "lock-out must be audit-logged");

		// The correct password no longer gets in
		mvc.perform(formLogin("/login").user(username).password(PASSWORD))
			.andExpect(redirectedUrl("/login?locked"));

		String lockedPage = mvc.perform(get("/login?locked")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertTrue(lockedPage.contains("locked-alert"), "login page must tell the user the account is locked");
	}

	@Test
	public void successfulLoginResetsTheFailureCounter() throws Exception {

		String username = createUser();

		for (int i = 0; i < 4; i++) {
			mvc.perform(formLogin("/login").user(username).password("wrong"));
		}
		mvc.perform(formLogin("/login").user(username).password(PASSWORD)).andExpect(redirectedUrl("/home"));
		for (int i = 0; i < 4; i++) {
			mvc.perform(formLogin("/login").user(username).password("wrong"));
		}

		assertTrue(userService.findByUsername(username).isAccountNonLocked(), "failures are not consecutive across a success");
	}

	// ---------------------------------------------------------------- payment OTP (web)

	@Test
	public void withdrawRequiresOtpBoundToAmount() throws Exception {

		Account account = sampleChecking();
		BigDecimal before = balance(account);

		MvcResult challenge = mvc.perform(post("/account/withdraw").with(user(SampleDataService.SMPL_MALE_EMAIL).roles("USER"))
				.param("id", account.getId().toString()).param("amount", "10.00"))
			.andExpect(status().isOk()).andReturn();

		assertEquals("payment-otp", challenge.getModelAndView().getViewName(), "withdraw must stop at the OTP step");
		assertEquals(0, before.compareTo(balance(account)), "no money moves without an OTP");

		String challengeId = (String) challenge.getModelAndView().getModel().get("otpChallengeId");
		String otp = lastOtp();

		// Same code, different amount -> rejected
		MvcResult tampered = mvc.perform(post("/account/withdraw").with(user(SampleDataService.SMPL_MALE_EMAIL).roles("USER"))
				.param("id", account.getId().toString()).param("amount", "11.00")
				.param("otpChallengeId", challengeId).param("otp", otp))
			.andExpect(status().isOk()).andReturn();
		assertEquals("payment-otp", tampered.getModelAndView().getViewName());
		assertEquals(0, before.compareTo(balance(account)), "OTP for $10 must not authorise $11");

		// Correct amount -> executed
		mvc.perform(post("/account/withdraw").with(user(SampleDataService.SMPL_MALE_EMAIL).roles("USER"))
				.param("id", account.getId().toString()).param("amount", "10.00")
				.param("otpChallengeId", challengeId).param("otp", otp))
			.andExpect(status().is3xxRedirection());
		assertEquals(0, before.subtract(new BigDecimal("10.00")).compareTo(balance(account)));

		// Replay -> rejected
		BigDecimal afterFirst = balance(account);
		MvcResult replay = mvc.perform(post("/account/withdraw").with(user(SampleDataService.SMPL_MALE_EMAIL).roles("USER"))
				.param("id", account.getId().toString()).param("amount", "10.00")
				.param("otpChallengeId", challengeId).param("otp", otp))
			.andExpect(status().isOk()).andReturn();
		assertEquals("payment-otp", replay.getModelAndView().getViewName());
		assertEquals(0, afterFirst.compareTo(balance(account)), "an OTP is single use");
	}

	@Test
	public void transferRequiresOtpBoundToPayee() throws Exception {

		Users owner = userService.findByUsername(SampleDataService.SMPL_MALE_EMAIL);
		Account from = sampleChecking();
		List<Account> others = accountService.getAllAccounts().stream()
				.filter(a -> !a.getId().equals(from.getId())).collect(Collectors.toList());
		Account payee = accountService.getSavingsAccounts(owner).get(0);
		Account otherPayee = others.stream().filter(a -> !a.getId().equals(payee.getId())).findFirst().get();

		BigDecimal fromBefore = balance(from);

		MvcResult challenge = mvc.perform(post("/account/xfer-between").with(user(SampleDataService.SMPL_MALE_EMAIL).roles("USER"))
				.param("fromAccount", from.getId().toString()).param("toAccount", payee.getId().toString()).param("amount", "5.00"))
			.andExpect(status().isOk()).andReturn();
		assertEquals("payment-otp", challenge.getModelAndView().getViewName(), "transfer must stop at the OTP step");
		assertEquals(0, fromBefore.compareTo(balance(from)));

		String challengeId = (String) challenge.getModelAndView().getModel().get("otpChallengeId");
		String otp = lastOtp();

		// Same code, different payee -> rejected
		MvcResult tampered = mvc.perform(post("/account/xfer-between").with(user(SampleDataService.SMPL_MALE_EMAIL).roles("USER"))
				.param("fromAccount", from.getId().toString()).param("toAccount", otherPayee.getId().toString()).param("amount", "5.00")
				.param("otpChallengeId", challengeId).param("otp", otp))
			.andExpect(status().isOk()).andReturn();
		assertEquals("payment-otp", tampered.getModelAndView().getViewName());
		assertEquals(0, fromBefore.compareTo(balance(from)), "OTP for one payee must not authorise another");

		mvc.perform(post("/account/xfer-between").with(user(SampleDataService.SMPL_MALE_EMAIL).roles("USER"))
				.param("fromAccount", from.getId().toString()).param("toAccount", payee.getId().toString()).param("amount", "5.00")
				.param("otpChallengeId", challengeId).param("otp", otp))
			.andExpect(status().is3xxRedirection());
		assertEquals(0, fromBefore.subtract(new BigDecimal("5.00")).compareTo(balance(from)));
	}

	// ---------------------------------------------------------------- payment OTP (REST)

	@Test
	public void restTransferRequiresOtpBoundToAmountAndPayee() throws Exception {

		Users owner = userService.findByUsername(SampleDataService.SMPL_MALE_EMAIL);
		Account from = sampleChecking();
		Account payee = accountService.getSavingsAccounts(owner).get(0);
		BigDecimal fromBefore = balance(from);

		String token = new ObjectMapper().readTree(mvc.perform(post("/api/v1/auth")
				.param("username", SampleDataService.SMPL_MALE_EMAIL).param("password", SampleDataService.SMPL_COMMON_PASSWORD))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("authToken").asText();

		String url = "/api/v1/account/" + from.getId() + "/transfer";

		MvcResult challenge = mvc.perform(post(url).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"toAccountId\":" + payee.getId() + ",\"amount\":7.00}"))
			.andExpect(status().is(428)).andReturn();
		assertEquals(0, fromBefore.compareTo(balance(from)));

		JsonNode body = new ObjectMapper().readTree(challenge.getResponse().getContentAsString());
		String challengeId = body.get("otpChallengeId").asText();
		String otp = lastOtp();

		mvc.perform(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"toAccountId\":" + payee.getId() + ",\"amount\":70.00,\"otpChallengeId\":\"" + challengeId + "\",\"otp\":\"" + otp + "\"}"))
			.andExpect(status().is(428));
		assertEquals(0, fromBefore.compareTo(balance(from)), "REST OTP for $7 must not authorise $70");

		challenge = mvc.perform(post(url).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"toAccountId\":" + payee.getId() + ",\"amount\":7.00}"))
			.andExpect(status().is(428)).andReturn();
		challengeId = new ObjectMapper().readTree(challenge.getResponse().getContentAsString()).get("otpChallengeId").asText();
		otp = lastOtp();

		mvc.perform(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"toAccountId\":" + payee.getId() + ",\"amount\":7.00,\"otpChallengeId\":\"" + challengeId + "\",\"otp\":\"" + otp + "\"}"))
			.andExpect(status().isOk());
		assertEquals(0, fromBefore.subtract(new BigDecimal("7.00")).compareTo(balance(from)));
	}

	// ---------------------------------------------------------------- remember-me

	@Test
	public void rememberMeCookieSignedWithPublicKeyIsRejected() throws Exception {

		Users jsmith = userService.findByUsername(SampleDataService.SMPL_MALE_EMAIL);
		long expiry = System.currentTimeMillis() + 3_600_000L;
		String signature = new String(Hex.encode(java.security.MessageDigest.getInstance("MD5").digest(
				(jsmith.getUsername() + ":" + expiry + ":" + jsmith.getPassword() + ":" + LEGACY_REMEMBER_ME_KEY)
						.getBytes(StandardCharsets.UTF_8))));
		String value = Base64.getEncoder().encodeToString(
				(jsmith.getUsername() + ":" + expiry + ":" + signature).getBytes(StandardCharsets.UTF_8));

		mvc.perform(get("/home").cookie(new Cookie("remember-me", value)))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern("**/login"));
	}

	@Test
	public void rememberMeSessionCannotInitiatePayments() throws Exception {

		Account account = sampleChecking();
		BigDecimal before = balance(account);

		RememberMeAuthenticationToken rememberMe = new RememberMeAuthenticationToken("any-key",
				new org.springframework.security.core.userdetails.User(SampleDataService.SMPL_MALE_EMAIL, "x",
						AuthorityUtils.createAuthorityList("ROLE_USER")),
				AuthorityUtils.createAuthorityList("ROLE_USER"));

		mvc.perform(post("/account/withdraw").with(authentication(rememberMe))
				.param("id", account.getId().toString()).param("amount", "1.00"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern("**/login"));

		assertEquals(0, before.compareTo(balance(account)), "remember-me must not move money");
	}

	// ---------------------------------------------------------------- helpers

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

	private Account sampleChecking() {
		return accountService.getCheckingAccounts(userService.findByUsername(SampleDataService.SMPL_MALE_EMAIL)).get(0);
	}

	private BigDecimal balance(Account account) {
		return accountService.getAccountById(account.getId()).getCurrentBalance();
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
