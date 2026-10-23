package io.digisic.bank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.digisic.bank.util.PiiCipher;

/**
 * Supplies the externally provided PII encryption key (property
 * <code>io.digisic.bank.pii.key</code> or env <code>IO_DIGISIC_BANK_PII_KEY</code>)
 * to the JPA attribute converters. Startup fails without a key unless the
 * database is in-memory, since data encrypted with an ephemeral key would be
 * unreadable after a restart.
 */
@Configuration
public class PiiEncryptionConfig {

	public PiiEncryptionConfig(@Value("${" + PiiCipher.KEY_PROPERTY + ":}") String key,
							   @Value("${spring.datasource.url:}") String datasourceUrl) {
		
		if (key != null && !key.trim().isEmpty()) {
			PiiCipher.configure(key);
			return;
		}
		
		if (!isInMemoryDatabase(datasourceUrl)) {
			throw new IllegalStateException("No PII encryption key supplied for persistent database " + datasourceUrl
					+ ". Set " + PiiCipher.KEY_ENV_VARIABLE + " (or " + PiiCipher.KEY_PROPERTY
					+ ") to a Base64 256-bit key, e.g. from: openssl rand -base64 32");
		}
	}
	
	private static boolean isInMemoryDatabase(String datasourceUrl) {
		
		return datasourceUrl == null || datasourceUrl.trim().isEmpty() || datasourceUrl.startsWith("jdbc:h2:mem:");
	}
}
