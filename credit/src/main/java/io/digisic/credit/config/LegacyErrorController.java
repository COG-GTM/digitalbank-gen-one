package io.digisic.credit.config;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.InvalidMediaTypeException;
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
	
	private static int specificity(MediaType type) {
		if (type.isWildcardType()) {
			return 0;
		}
		if (!type.isWildcardSubtype()) {
			return 3;
		}
		return type.getSubtypeSuffix() != null ? 2 : 1;
	}
	
	private static boolean acceptsHal(HttpServletRequest request) {
		
		String accept = request.getHeader("Accept");
		if (accept == null || accept.isBlank()) {
			return true;
		}
		try {
			// the most specific entry covering HAL decides, so an explicit q=0 exclusion wins over */*
			return MediaType.parseMediaTypes(accept).stream()
				.filter(m -> m.includes(MediaTypes.HAL_JSON))
				.max(Comparator.comparingInt(LegacyErrorController::specificity))
				.map(m -> m.getQualityValue() > 0)
				.orElse(false);
		} catch (InvalidMediaTypeException e) {
			return false;
		}
	}
}
