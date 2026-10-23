package io.digisic.bank.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encrypts personal data (national ID numbers, dates of birth) for storage at rest.
 * 
 * AES-256-GCM with a synthetic IV derived from HMAC-SHA256 of the plaintext, so the
 * same value always produces the same ciphertext. This keeps equality lookups
 * (<code>findBySsn</code>) and the unique constraint on <code>ssn</code> working.
 * 
 * The 256-bit key is supplied externally as Base64 via the
 * <code>io.digisic.bank.pii.key</code> property / <code>IO_DIGISIC_BANK_PII_KEY</code>
 * environment variable. Encryption and MAC sub-keys are derived from it.
 */
public final class PiiCipher {
	
	private static final Logger LOG = LoggerFactory.getLogger(PiiCipher.class);
	
	public static final String KEY_PROPERTY		= "io.digisic.bank.pii.key";
	public static final String KEY_ENV_VARIABLE	= "IO_DIGISIC_BANK_PII_KEY";
	public static final String PREFIX			= "enc:v1:";
	
	private static final int KEY_LENGTH			= 32;
	private static final int IV_LENGTH			= 12;
	private static final int TAG_LENGTH_BITS	= 128;
	
	private static volatile PiiCipher instance;
	
	private final SecretKeySpec encryptionKey;
	private final SecretKeySpec macKey;
	
	public PiiCipher(byte[] masterKey) {
		
		if (masterKey == null || masterKey.length != KEY_LENGTH) {
			throw new IllegalArgumentException("PII encryption key must be " + KEY_LENGTH + " bytes (Base64 encoded)");
		}
		
		this.encryptionKey = new SecretKeySpec(derive(masterKey, "digitalbank-pii-enc"), "AES");
		this.macKey = new SecretKeySpec(derive(masterKey, "digitalbank-pii-mac"), "HmacSHA256");
	}
	
	public static PiiCipher fromBase64Key(String base64Key) {
		
		return new PiiCipher(Base64.getDecoder().decode(base64Key.trim()));
	}
	
	/**
	 * Sets the application wide cipher from the externally supplied key.
	 */
	public static synchronized void configure(String base64Key) {
		
		instance = fromBase64Key(base64Key);
	}
	
	/**
	 * Returns the application wide cipher. If no key was configured, the key is read
	 * from the system property or environment variable. If none is supplied, an
	 * ephemeral random key is generated: only acceptable with the default in-memory
	 * database, since persisted values cannot be decrypted after a restart.
	 */
	public static PiiCipher getInstance() {
		
		PiiCipher current = instance;
		
		if (current == null) {
			synchronized (PiiCipher.class) {
				if (instance == null) {
					instance = fromEnvironment();
				}
				current = instance;
			}
		}
		
		return current;
	}
	
	private static PiiCipher fromEnvironment() {
		
		String key = System.getProperty(KEY_PROPERTY);
		
		if (key == null || key.trim().isEmpty()) {
			key = System.getenv(KEY_ENV_VARIABLE);
		}
		
		if (key != null && !key.trim().isEmpty()) {
			return fromBase64Key(key);
		}
		
		LOG.warn("No PII encryption key supplied (" + KEY_PROPERTY + " / " + KEY_ENV_VARIABLE + "). "
				+ "Using an ephemeral key: encrypted data will be unreadable after restart.");
		
		byte[] random = new byte[KEY_LENGTH];
		new SecureRandom().nextBytes(random);
		
		return new PiiCipher(random);
	}
	
	public String encrypt(String plaintext) {
		
		if (plaintext == null) {
			return null;
		}
		
		try {
			
			byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
			
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(macKey);
			byte[] iv = Arrays.copyOf(mac.doFinal(data), IV_LENGTH);
			
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] encrypted = cipher.doFinal(data);
			
			byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
			
			return PREFIX + Base64.getEncoder().encodeToString(out);
			
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Unable to encrypt PII value", e);
		}
	}
	
	public String decrypt(String ciphertext) {
		
		if (ciphertext == null) {
			return null;
		}
		
		if (!isEncrypted(ciphertext)) {
			throw new IllegalArgumentException("Value is not PII ciphertext");
		}
		
		try {
			
			byte[] in = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
			
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, in, 0, IV_LENGTH));
			byte[] plain = cipher.doFinal(in, IV_LENGTH, in.length - IV_LENGTH);
			
			return new String(plain, StandardCharsets.UTF_8);
			
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Unable to decrypt PII value", e);
		}
	}
	
	public static boolean isEncrypted(String value) {
		
		return value != null && value.startsWith(PREFIX);
	}
	
	private static byte[] derive(byte[] masterKey, String label) {
		
		try {
			
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
			
			return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
			
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Unable to derive PII sub-key", e);
		}
	}
}
