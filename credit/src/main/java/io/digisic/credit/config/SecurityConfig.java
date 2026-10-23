package io.digisic.credit.config;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import java.util.Arrays;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

import io.digisic.credit.security.JwtTokenFilterConfigurer;
import io.digisic.credit.security.JwtTokenProvider;
import io.digisic.credit.service.UserSecurityService;
import io.digisic.credit.util.Constants;
import io.digisic.credit.util.Patterns;


@Configuration
@EnableWebSecurity(debug=false)
@EnableMethodSecurity
public class SecurityConfig
{
	private static final String[] PUBLIC = {
			
			Constants.URI_WEBJARS_RES, 
	    	Constants.URI_CSS_RES, 
	    	Constants.URI_SCSS_RES, 
	    	Constants.URI_FONTS_RES, 
	    	Constants.URI_JS_RES, 
	    	Constants.URI_IMAGES_RES, 
	    	Constants.URI_ROOT,
	    	Constants.URI_H2_CONSOLE,
	    	Constants.URI_SWAGGER_UI,
	    	Constants.URI_SWAGGER_V2,
	    	Constants.URI_SWAGGER_RES,
	    	Constants.URI_SWAGGER_CONF,
	    	// springdoc: /swagger-ui.html redirects to these
	    	"/swagger-ui/**",
	    	"/v3/api-docs/**"
	};
	
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
       return configuration.getAuthenticationManager();
	}
  
	// static: UserService injects this encoder while this config injects services
	@Bean
	public static BCryptPasswordEncoder encoder() {
		return new BCryptPasswordEncoder();
	}
  
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
    
		RequestMatcher[] publicMatchers = Arrays.stream(PUBLIC).map(p -> antMatcher(p)).toArray(RequestMatcher[]::new);
		
		http.securityMatcher(antMatcher(Constants.URI_ALL))
			.csrf(csrf -> csrf.disable())
			.exceptionHandling(e -> e.accessDeniedPage(Constants.URI_API_AUTH))
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(a -> a
				// legacy FilterSecurityInterceptor authorised each request once (no FORWARD/ERROR re-checks)
				.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
				.requestMatchers(publicMatchers).permitAll()
				.requestMatchers(antMatcher(HttpMethod.GET, Constants.URI_API_HEALTHCHECK)).permitAll()
				.requestMatchers(antMatcher(HttpMethod.POST, Constants.URI_API_AUTH)).permitAll()
				.anyRequest().hasRole(Patterns.ROLE_API))
			.with(new JwtTokenFilterConfigurer(jwtTokenProvider), c -> {})
			// Needed to support the h2-console interface
			.headers(h -> h.frameOptions(f -> f.disable()));
		
		return http.build();
  }
  
	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth, UserSecurityService userSecurityService) throws Exception {
		auth.userDetailsService(userSecurityService).passwordEncoder(encoder());
	}
}
