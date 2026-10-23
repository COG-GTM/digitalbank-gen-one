package io.digisic.bank.test.junit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.digisic.bank.config.data.PiiDataMigration;
import io.digisic.bank.model.UserProfile;
import io.digisic.bank.repository.UserProfileRepository;
import io.digisic.bank.service.UserService;

/**
 * EM-142: national ID numbers (SSN) and dates of birth must be encrypted at rest
 * and SSNs must never be written to logs in full.
 */
@DataJpaTest
@ExtendWith(SpringExtension.class)
public class PiiDataProtectionTest {

	private static final String SSN			= "123-45-6789";
	private static final String MASKED_SSN	= "***-**-6789";

	@Autowired
	private UserProfileRepository userProfileRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	public void ssnAndDobAreStoredAsCiphertextAndReadBackTransparently() {

		Date dob = new Date(315532800000L);
		UserProfile saved = userProfileRepository.save(newProfile(SSN, dob));
		entityManager.flush();
		entityManager.clear();

		Object[] row = (Object[]) entityManager
				.createNativeQuery("select ssn, dob from user_profile where id = ?1")
				.setParameter(1, saved.getId())
				.getSingleResult();

		String rawSsn = String.valueOf(row[0]);
		String rawDob = String.valueOf(row[1]);

		assertFalse(rawSsn.contains("6789"), "SSN column must not contain the plaintext: " + rawSsn);
		assertTrue(rawSsn.startsWith("enc:v1:"), "SSN column must hold ciphertext: " + rawSsn);
		assertTrue(rawDob.startsWith("enc:v1:"), "DOB column must hold ciphertext: " + rawDob);

		UserProfile loaded = userProfileRepository.findById(saved.getId()).get();
		assertEquals(SSN, loaded.getSsn());
		assertEquals(dob.getTime(), loaded.getDob().getTime());
	}

	@Test
	public void findBySsnStillMatchesEncryptedColumn() {

		userProfileRepository.save(newProfile(SSN, new Date()));
		entityManager.flush();
		entityManager.clear();

		UserProfile found = userProfileRepository.findBySsn(SSN);

		assertNotNull(found);
		assertEquals(SSN, found.getSsn());
	}

	@Test
	public void legacyPlaintextSsnIsStillDetectedAsDuplicate() {

		Long id = saveLegacyPlaintextProfile();

		UserService userService = new UserService();
		ReflectionTestUtils.setField(userService, "userProfileRepository", userProfileRepository);

		assertEquals(SSN, rawSsn(id));
		assertTrue(userService.checkSsnExists(SSN), "Plaintext legacy SSN must block duplicate registration");
	}

	@Test
	public void migrationEncryptsLegacyPlaintextSsns() {

		Long id = saveLegacyPlaintextProfile();

		PiiDataMigration migration = new PiiDataMigration();
		ReflectionTestUtils.setField(migration, "userProfileRepository", userProfileRepository);
		migration.run();
		entityManager.clear();

		assertTrue(rawSsn(id).startsWith("enc:v1:"), "Legacy SSN must be encrypted: " + rawSsn(id));
		assertEquals(SSN, userProfileRepository.findBySsn(SSN).getSsn());
	}

	@Test
	public void ssnIsMaskedInUserServiceDebugLogs() {

		Logger logger = (Logger) LoggerFactory.getLogger(UserService.class);
		Level previous = logger.getLevel();
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		logger.setLevel(Level.DEBUG);

		try {

			UserService userService = new UserService();
			ReflectionTestUtils.setField(userService, "userProfileRepository", Mockito.mock(UserProfileRepository.class));

			userService.checkSsnExists("123456789");
			userService.checkSsnExists(SSN);

		} finally {
			logger.detachAppender(appender);
			logger.setLevel(previous);
		}

		List<String> messages = appender.list.stream()
				.map(ILoggingEvent::getFormattedMessage)
				.collect(Collectors.toList());

		assertFalse(messages.isEmpty(), "Expected debug log output from SSN normalization");

		for (String message : messages) {
			assertFalse(message.contains("123456789"), "Full SSN logged: " + message);
			assertFalse(message.contains(SSN), "Full SSN logged: " + message);
		}

		assertTrue(messages.stream().anyMatch(m -> m.contains(MASKED_SSN)), "Masked SSN expected in: " + messages);
	}

	@Test
	public void userProfileToStringMasksSsn() {

		String text = newProfile(SSN, new Date()).toString();

		assertFalse(text.contains(SSN), text);
		assertTrue(text.contains(MASKED_SSN), text);
	}

	private Long saveLegacyPlaintextProfile() {

		Long id = userProfileRepository.save(newProfile(SSN, new Date())).getId();
		entityManager.flush();
		entityManager.createNativeQuery("update user_profile set ssn = ?1 where id = ?2")
				.setParameter(1, SSN)
				.setParameter(2, id)
				.executeUpdate();
		entityManager.clear();

		return id;
	}

	private String rawSsn(Long id) {

		return String.valueOf(entityManager
				.createNativeQuery("select ssn from user_profile where id = ?1")
				.setParameter(1, id)
				.getSingleResult());
	}

	private static UserProfile newProfile(String ssn, Date dob) {

		UserProfile profile = new UserProfile();
		profile.setFirstName("Jane");
		profile.setLastName("Doe");
		profile.setTitle("Ms.");
		profile.setGender("F");
		profile.setSsn(ssn);
		profile.setDob(dob);
		profile.setEmailAddress("jane.doe@example.com");
		profile.setHomePhone("555-555-5555");
		profile.setAddress("1 Main St");
		profile.setLocality("Springfield");
		profile.setRegion("IL");
		profile.setPostalCode("62701");
		profile.setCountry("US");

		return profile;
	}
}
