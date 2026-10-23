package io.digisic.bank.security.sca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.digisic.bank.model.security.Users;

/*
 * Development stand-in for an SMS/push gateway: writes the passcode to the server log.
 * Replace with a real out-of-band OtpSender bean before production use.
 */
@Component
public class LoggingOtpSender implements OtpSender {

	private static final Logger LOG = LoggerFactory.getLogger(LoggingOtpSender.class);

	@Override
	public void send(Users user, String code, String description) {

		String phone = user.getUserProfile() != null ? user.getUserProfile().getMobilePhone() : null;
		String masked = (phone != null && phone.length() > 4) ? "***" + phone.substring(phone.length() - 4) : "***";

		LOG.warn("[DEV OTP DELIVERY] To '{}' (mobile {}): code {} authorises {}", user.getUsername(), masked, code, description);
	}
}
