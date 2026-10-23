package io.digisic.bank.controller.web;

import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Spring 6.1 raises NoResourceFoundException for unmapped paths (e.g. /no-such-page, /promotions), whose detail
 * ("No static resource ...") would leak into the error body. Spring Boot 2.1 answered those 404s without an
 * exception, so the legacy body carries the default message.
 */
@Component
public class WebErrorAttributes extends DefaultErrorAttributes {

	private static final String NO_MESSAGE_AVAILABLE = "No message available";

	@Override
	protected String getMessage(WebRequest webRequest, Throwable error) {
		if (error instanceof NoResourceFoundException) {
			return NO_MESSAGE_AVAILABLE;
		}
		return super.getMessage(webRequest, error);
	}
}
