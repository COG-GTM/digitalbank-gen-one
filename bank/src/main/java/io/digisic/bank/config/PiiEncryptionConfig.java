package io.digisic.bank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.digisic.bank.util.PiiCipher;

/**
 * Supplies the externally provided PII encryption key (property
 * <code>io.digisic.bank.pii.key</code> or env <code>IO_DIGISIC_BANK_PII_KEY</code>)
 * to the JPA attribute converters.
 */
@Configuration
public class PiiEncryptionConfig {

	public PiiEncryptionConfig(@Value("${" + PiiCipher.KEY_PROPERTY + ":}") String key) {
		
		if (key != null && !key.trim().isEmpty()) {
			PiiCipher.configure(key);
		}
	}
}
