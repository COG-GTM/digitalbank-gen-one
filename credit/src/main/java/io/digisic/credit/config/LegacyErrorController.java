package io.digisic.credit.config;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/*
 * Boot 2.1 (with Spring Data REST on the classpath) answered requests that matched
 * no handler with an application/hal+json error body; keep that content type.
 */
@Controller
public class LegacyErrorController extends BasicErrorController {

	private final ErrorAttributes errorAttributes;

	public LegacyErrorController(ErrorAttributes errorAttributes, ServerProperties serverProperties,
								 List<ErrorViewResolver> errorViewResolvers) {
		super(errorAttributes, serverProperties.getError(), errorViewResolvers);
		this.errorAttributes = errorAttributes;
	}

	@Override
	public ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
		
		ResponseEntity<Map<String, Object>> response = super.error(request);
		
		if (errorAttributes.getError(new ServletWebRequest(request)) instanceof NoResourceFoundException
				&& acceptsHal(request)) {
			return ResponseEntity.status(response.getStatusCode()).contentType(MediaTypes.HAL_JSON).body(response.getBody());
		}
		return response;
	}
	
	private static boolean acceptsHal(HttpServletRequest request) {
		
		String accept = request.getHeader("Accept");
		if (accept == null || accept.isBlank()) {
			return true;
		}
		return MediaType.parseMediaTypes(accept).stream().anyMatch(m -> m.isCompatibleWith(MediaTypes.HAL_JSON));
	}
}
