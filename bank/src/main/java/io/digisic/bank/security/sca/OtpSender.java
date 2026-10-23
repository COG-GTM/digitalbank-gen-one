package io.digisic.bank.security.sca;

import io.digisic.bank.model.security.Users;

/*
 * Out-of-band delivery channel for one-time passcodes (SMS, push, hardware token, ...).
 */
public interface OtpSender {

	void send(Users user, String code, String description);
}
