package io.digisic.credit.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class RandomSecrets {

	private static final SecureRandom RANDOM = new SecureRandom();

	private RandomSecrets() {
	}

	public static String password() {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String orRandom(String configured) {
		return (configured == null || configured.trim().isEmpty()) ? password() : configured;
	}
}
