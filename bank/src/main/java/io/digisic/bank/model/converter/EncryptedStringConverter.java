package io.digisic.bank.model.converter;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import io.digisic.bank.util.PiiCipher;

/**
 * Stores a String attribute encrypted at rest and decrypts it transparently on read.
 * Values written before encryption was enabled (no ciphertext prefix) are read as-is
 * and are encrypted the next time the entity is saved.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

	@Override
	public String convertToDatabaseColumn(String attribute) {
		
		return PiiCipher.getInstance().encrypt(attribute);
	}

	@Override
	public String convertToEntityAttribute(String dbData) {
		
		if (dbData == null || !PiiCipher.isEncrypted(dbData)) {
			return dbData;
		}
		
		return PiiCipher.getInstance().decrypt(dbData);
	}
}
