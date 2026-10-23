package io.digisic.bank.test.junit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

import io.digisic.bank.config.PiiEncryptionConfig;
import io.digisic.bank.model.converter.EncryptedDateConverter;
import io.digisic.bank.model.converter.EncryptedStringConverter;
import io.digisic.bank.util.PiiCipher;
import io.digisic.bank.util.PiiMasker;

public class PiiCipherTest {

	private static final String SSN = "123-45-6789";

	@Test
	public void startupFailsWithoutKeyForPersistentDatabase() {

		assertThrows(IllegalStateException.class, () -> new PiiEncryptionConfig("", "jdbc:h2:file:/tmp/digitalbank"));
		assertThrows(IllegalStateException.class, () -> new PiiEncryptionConfig(null, "jdbc:mysql://localhost:3306/digitalbank"));

		new PiiEncryptionConfig("", "");
		new PiiEncryptionConfig("", "jdbc:h2:mem:testdb");
	}

	@Test
	public void cipherRoundTripsAndHidesPlaintext() {

		PiiCipher cipher = PiiCipher.fromBase64Key(randomKey());

		String encrypted = cipher.encrypt(SSN);

		assertTrue(PiiCipher.isEncrypted(encrypted));
		assertFalse(encrypted.contains("6789"));
		assertEquals(SSN, cipher.decrypt(encrypted));
	}

	@Test
	public void encryptionIsDeterministicPerKeySoLookupsWork() {

		PiiCipher cipher = PiiCipher.fromBase64Key(randomKey());

		assertEquals(cipher.encrypt(SSN), cipher.encrypt(SSN));
		assertNotEquals(cipher.encrypt(SSN), cipher.encrypt("987-65-4321"));
		assertNotEquals(cipher.encrypt(SSN), PiiCipher.fromBase64Key(randomKey()).encrypt(SSN));
	}

	@Test
	public void wrongKeyOrTamperedCiphertextIsRejected() {

		PiiCipher cipher = PiiCipher.fromBase64Key(randomKey());
		String encrypted = cipher.encrypt(SSN);
		PiiCipher other = PiiCipher.fromBase64Key(randomKey());

		assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));

		char last = encrypted.charAt(encrypted.length() - 2);
		String tampered = encrypted.substring(0, encrypted.length() - 2) + (last == 'A' ? 'B' : 'A') + encrypted.charAt(encrypted.length() - 1);
		assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
	}

	@Test
	public void keyMustBe256Bits() {

		assertThrows(IllegalArgumentException.class,
				() -> PiiCipher.fromBase64Key(Base64.getEncoder().encodeToString(new byte[16])));
	}

	@Test
	public void convertersRoundTrip() {

		EncryptedStringConverter ssnConverter = new EncryptedStringConverter();
		String column = ssnConverter.convertToDatabaseColumn(SSN);

		assertTrue(PiiCipher.isEncrypted(column));
		assertEquals(SSN, ssnConverter.convertToEntityAttribute(column));
		assertEquals(SSN, ssnConverter.convertToEntityAttribute(SSN), "legacy plaintext rows stay readable");
		assertNull(ssnConverter.convertToDatabaseColumn(null));

		EncryptedDateConverter dobConverter = new EncryptedDateConverter();
		Date dob = new Date(315532800123L);
		String dobColumn = dobConverter.convertToDatabaseColumn(dob);

		assertTrue(PiiCipher.isEncrypted(dobColumn));
		assertEquals(dob, dobConverter.convertToEntityAttribute(dobColumn));
		assertNull(dobConverter.convertToEntityAttribute(null));
	}

	@Test
	public void maskerKeepsOnlyLastFourDigits() {

		assertEquals("***-**-6789", PiiMasker.maskSsn("123-45-6789"));
		assertEquals("***-**-6789", PiiMasker.maskSsn("123456789"));
		assertEquals("***-**-****", PiiMasker.maskSsn("12"));
		assertNull(PiiMasker.maskSsn(null));
		assertEquals("**/**/****", PiiMasker.maskDate(new Date()));
	}

	private static String randomKey() {

		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}
}
