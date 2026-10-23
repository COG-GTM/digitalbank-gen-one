package io.digisic.bank.model.converter;

import java.util.Date;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import io.digisic.bank.util.PiiCipher;

/**
 * Stores a Date attribute (epoch milliseconds) encrypted at rest and decrypts it
 * transparently on read.
 */
@Converter
public class EncryptedDateConverter implements AttributeConverter<Date, String> {

	@Override
	public String convertToDatabaseColumn(Date attribute) {
		
		if (attribute == null) {
			return null;
		}
		
		return PiiCipher.getInstance().encrypt(Long.toString(attribute.getTime()));
	}

	@Override
	public Date convertToEntityAttribute(String dbData) {
		
		if (dbData == null) {
			return null;
		}
		
		return new Date(Long.parseLong(PiiCipher.getInstance().decrypt(dbData)));
	}
}
