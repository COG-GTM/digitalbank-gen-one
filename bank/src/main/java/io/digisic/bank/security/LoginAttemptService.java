package io.digisic.bank.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import io.digisic.bank.repository.UserRepository;

/*
 * Locks a user after consecutive failed password attempts (SCA-RTS Art. 4(3)(b): max. 5).
 * Applies to both form login and the API token endpoint, which share the authentication manager.
 */
@Component
public class LoginAttemptService {

	private static final Logger AUDIT = LoggerFactory.getLogger("io.digisic.bank.audit");

	@Autowired
	private UserRepository userRepository;

	@Value("${io.digisic.bank.security.max-failed-logins:5}")
	private int maxFailedLogins;

	@EventListener
	public void onFailure(AuthenticationFailureBadCredentialsEvent event) {

		String username = String.valueOf(event.getAuthentication().getPrincipal());

		if (userRepository.incrementFailedLoginAttempts(username) == 0) {
			AUDIT.warn("event=LOGIN_FAILURE user={} reason=unknown_user", username);
			return;
		}

		Integer failures = userRepository.findFailedLoginAttempts(username);

		AUDIT.warn("event=LOGIN_FAILURE user={} consecutiveFailures={}", username, failures);

		if (failures != null && failures >= maxFailedLogins && userRepository.lock(username) == 1) {
			AUDIT.warn("event=ACCOUNT_LOCKED user={} reason=max_failed_logins consecutiveFailures={}", username, failures);
		}
	}

	@EventListener
	public void onSuccess(AuthenticationSuccessEvent event) {

		if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
			return;
		}

		userRepository.resetFailedLoginAttempts(event.getAuthentication().getName());
	}
}
