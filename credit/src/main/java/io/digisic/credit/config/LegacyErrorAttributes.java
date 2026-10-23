package io.digisic.credit.config;

import org.springframework.beans.TypeMismatchException;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.core.JacksonException;

/*
 * Keeps the Spring 5 / Boot 2.1 wording of framework error messages in the
 * REST error payload ("message" attribute) so API clients see identical bodies.
 */
@Component
public class LegacyErrorAttributes extends DefaultErrorAttributes {

	static final String NO_MESSAGE = "No message available";
	
	private static final String JACKSON_REDACTED_SOURCE = "REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled)";
	private static final String LEGACY_SOURCE = "(PushbackInputStream)";

	@Override
	protected String getMessage(WebRequest webRequest, Throwable error) {
		
		if (error instanceof NoResourceFoundException) {
			return NO_MESSAGE;
		}
		if (error instanceof MissingServletRequestParameterException ex) {
			return "Required " + ex.getParameterType() + " parameter '" + ex.getParameterName() + "' is not present";
		}
		if (error instanceof HttpRequestMethodNotSupportedException ex) {
			return "Request method '" + ex.getMethod() + "' not supported";
		}
		if (error instanceof TypeMismatchException ex) {
			return typeMismatchMessage(ex);
		}
		if (error instanceof HttpMessageNotReadableException ex && ex.getCause() instanceof JacksonException cause) {
			return ex.getMessage() + "; nested exception is " + cause.toString().replace(JACKSON_REDACTED_SOURCE, LEGACY_SOURCE);
		}
		return super.getMessage(webRequest, error);
	}
	
	private static String typeMismatchMessage(TypeMismatchException ex) {
		
		String msg = "Failed to convert value of type '" + ClassUtils.getDescriptiveType(ex.getValue()) + "'"
				   + (ex.getRequiredType() != null ? " to required type '" + ClassUtils.getQualifiedName(ex.getRequiredType()) + "'" : "");
		
		Throwable cause = ex.getCause();
		while (cause instanceof ConversionFailedException && cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause != null ? msg + "; nested exception is " + cause : msg;
	}
}
