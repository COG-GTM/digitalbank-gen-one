package io.digisic.bank.security;

import java.util.Map;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/*
 * Keeps the Spring 5 / Boot 2.1 "message" text of the JSON error body. The API security chain answers a JWT without
 * the required role by forwarding to the POST-only /api/v1/auth endpoint (405), so the wording of these errors is part
 * of the observable security behaviour.
 */
public class LegacyErrorAttributes extends DefaultErrorAttributes {

	private static final String MESSAGE = "message";

	@Override
	public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {

		Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);

		if (attributes.containsKey(MESSAGE)) {
			String legacyMessage = legacyMessage(getError(webRequest));
			if (legacyMessage != null) {
				attributes.put(MESSAGE, legacyMessage);
			}
		}

		return attributes;
	}

	private static String legacyMessage(Throwable error) {

		if (error instanceof HttpRequestMethodNotSupportedException ex) {
			return "Request method '" + ex.getMethod() + "' not supported";
		}
		if (error instanceof MissingServletRequestParameterException ex) {
			return "Required " + ex.getParameterType() + " parameter '" + ex.getParameterName() + "' is not present";
		}
		if (error instanceof NoResourceFoundException) {
			return "No message available";
		}
		return null;
	}

}
