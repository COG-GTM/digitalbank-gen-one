package io.digisic.bank.exception;

import java.util.Map;

import org.springframework.beans.TypeMismatchException;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/*
 * Keeps the "message" of the JSON error body identical to Spring Boot 2.1 / Spring 5.1:
 * Spring 6 reworded the framework exceptions (ProblemDetail "detail" texts) and dropped the
 * "; nested exception is ..." suffix that Spring 5 appended to nested exception messages.
 */
@Component
public class LegacyErrorAttributes extends DefaultErrorAttributes {

	private static final String MESSAGE = "message";
	private static final String NO_MESSAGE = "No message available";

	@Override
	public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {

		Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);

		if (attributes.containsKey(MESSAGE)) {
			String legacy = legacyMessage(getError(webRequest));
			if (legacy != null) {
				attributes.put(MESSAGE, legacy);
			}
		}

		return attributes;
	}

	static String legacyMessage(Throwable error) {

		if (error instanceof MissingServletRequestParameterException ex) {
			return "Required " + ex.getParameterType() + " parameter '" + ex.getParameterName() + "' is not present";
		}
		if (error instanceof HttpRequestMethodNotSupportedException ex) {
			return "Request method '" + ex.getMethod() + "' not supported";
		}
		if (error instanceof HttpMediaTypeNotSupportedException ex) {
			return "Content type '" + (ex.getContentType() != null ? ex.getContentType() : "") + "' not supported";
		}
		if (error instanceof HttpMediaTypeNotAcceptableException) {
			return "Could not find acceptable representation";
		}
		if (error instanceof NoResourceFoundException || error instanceof NoHandlerFoundException) {
			return NO_MESSAGE;
		}
		if (error instanceof MethodArgumentTypeMismatchException ex) {
			return typeMismatchMessage(ex);
		}
		if (error instanceof HttpMessageNotReadableException ex && ex.getCause() != null
				&& ex.getMessage() != null && !ex.getMessage().contains("; nested exception is ")) {
			return ex.getMessage() + "; nested exception is " + ex.getCause();
		}
		return null;
	}

	private static String typeMismatchMessage(TypeMismatchException ex) {

		StringBuilder message = new StringBuilder("Failed to convert value of type '")
			.append(ClassUtils.getDescriptiveType(ex.getValue())).append("'");

		if (ex.getRequiredType() != null) {
			message.append(" to required type '").append(ClassUtils.getQualifiedName(ex.getRequiredType())).append("'");
		}
		if (ex.getCause() != null) {
			message.append("; nested exception is ").append(ex.getCause());
		}
		return message.toString();
	}
}
