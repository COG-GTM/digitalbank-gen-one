package io.digisic.bank.exception;

import java.io.IOException;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
 * Boot 2.1 / Spring Security 5.1 created an HTTP session on every error dispatch (the form-login
 * chain with SessionCreationPolicy.ALWAYS handled /error), so API error responses carried a
 * JSESSIONID cookie. Spring Security 6 skips eager session creation on ERROR dispatches; this
 * filter restores the legacy observable behaviour.
 */
@Configuration
public class ErrorDispatchSessionConfig {

	@Bean
	public FilterRegistrationBean<OncePerRequestFilter> errorDispatchSessionFilter() {

		OncePerRequestFilter filter = new OncePerRequestFilter() {

			@Override
			protected boolean shouldNotFilterErrorDispatch() {
				return false;
			}

			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
					FilterChain chain) throws ServletException, IOException {
				request.getSession(true);
				chain.doFilter(request, response);
			}
		};

		FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setDispatcherTypes(DispatcherType.ERROR);
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}
}
