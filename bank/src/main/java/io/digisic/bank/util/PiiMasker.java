package io.digisic.bank.util;

/**
 * Masks personal identifiers before they are written to logs or other
 * diagnostic output, e.g. an SSN becomes <code>***-**-1234</code>.
 */
public final class PiiMasker {

	public static final String MASKED_SSN_PREFIX	= "***-**-";
	public static final String MASKED_DATE			= "**/**/****";
	
	private PiiMasker() {}
	
	public static String maskSsn(String ssn) {
		
		if (ssn == null) {
			return null;
		}
		
		String digits = ssn.replaceAll("\\D", "");
		
		if (digits.length() < 4) {
			return MASKED_SSN_PREFIX + "****";
		}
		
		return MASKED_SSN_PREFIX + digits.substring(digits.length() - 4);
	}
	
	public static String maskDate(Object date) {
		
		return date == null ? null : MASKED_DATE;
	}
}
