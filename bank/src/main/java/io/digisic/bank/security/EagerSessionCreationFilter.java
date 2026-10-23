package io.digisic.bank.security;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.filter.GenericFilterBean;

/*
 * Spring Security 5 (SessionCreationPolicy.ALWAYS) created the HTTP session on every dispatch of the form login chain,
 * including the ERROR dispatch to /error, so error responses (also those of the stateless API) carried a JSESSIONID
 * cookie. Spring Security 6's ForceEagerSessionCreationFilter skips ERROR dispatches; this filter restores that.
 */
public class EagerSessionCreationFilter extends GenericFilterBean {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

		if (request instanceof HttpServletRequest httpRequest
				&& httpRequest.getDispatcherType() == DispatcherType.ERROR
				&& !response.isCommitted()) {
			httpRequest.getSession();
		}

		chain.doFilter(request, response);
	}

}
