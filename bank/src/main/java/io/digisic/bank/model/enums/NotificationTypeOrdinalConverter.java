package io.digisic.bank.model.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link NotificationType} as its ordinal in a plain INTEGER column, as the legacy Hibernate 5 build did.
 * Hibernate 6's built-in ordinal mapping would use TINYINT plus a CHECK constraint on the value range.
 */
@Converter
public class NotificationTypeOrdinalConverter implements AttributeConverter<NotificationType, Integer> {

	private static final NotificationType[] VALUES = NotificationType.values();

	@Override
	public Integer convertToDatabaseColumn(NotificationType attribute) {
		return attribute == null ? null : attribute.ordinal();
	}

	@Override
	public NotificationType convertToEntityAttribute(Integer dbData) {
		return dbData == null ? null : VALUES[dbData];
	}
}
