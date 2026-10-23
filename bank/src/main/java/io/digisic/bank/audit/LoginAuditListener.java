package io.digisic.bank.audit;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/*
 * Records every login attempt (web form and REST API) published by Spring Security.
 */
@Component
public class LoginAuditListener {

	@EventListener
	public void onSuccess(AuthenticationSuccessEvent event) {
		Authentication auth = event.getAuthentication();
		AuditLog.login(auth.getName(), true, channel(auth), null);
	}

	@EventListener
	public void onFailure(AbstractAuthenticationFailureEvent event) {
		Authentication auth = event.getAuthentication();
		AuditLog.login(auth.getName(), false, channel(auth), event.getException().getClass().getSimpleName());
	}

	private String channel(Authentication auth) {
		return auth.getDetails() instanceof WebAuthenticationDetails ? "WEB" : "API";
	}
}
