package io.digisic.bank.config;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import java.util.Arrays;

import jakarta.servlet.DispatcherType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.session.ForceEagerSessionCreationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import io.digisic.bank.security.EagerSessionCreationFilter;
import io.digisic.bank.security.JwtTokenFilterConfigurer;
import io.digisic.bank.security.JwtTokenProvider;
import io.digisic.bank.service.UserSecurityService;
import io.digisic.bank.util.Constants;
import io.digisic.bank.util.Patterns;

@Configuration
@EnableWebSecurity(debug=false)
@EnableMethodSecurity
public class MultiHttpSecurityConfig {

	private static final Logger LOG = LoggerFactory.getLogger(MultiHttpSecurityConfig.class);

	private static final String[] PUBLIC = {
		
			Constants.URI_WEBJARS_RES, 
	    	Constants.URI_CSS_RES, 
	    	Constants.URI_SCSS_RES, 
	    	Constants.URI_FONTS_RES, 
	    	Constants.URI_JS_RES, 
	    	Constants.URI_IMAGES_RES, 
	    	Constants.URI_REGISTER, 
	    	Constants.URI_ABOUT_RES, 
	    	Constants.URI_CONTACT_RES, 
	    	Constants.URI_ERROR_RES,
	    	Constants.URI_ROOT,
	    	Constants.URI_SIGNUP,
	    	Constants.URI_H2_CONSOLE,
	    	Constants.URI_SWAGGER_UI,
	    	Constants.URI_SWAGGER_V2,
	    	Constants.URI_SWAGGER_RES,
	    	Constants.URI_SWAGGER_CONF,
	    	Constants.URI_MANAGER_RES,
	    	Constants.URI_FAVICON_RES,
	    	// springdoc: /swagger-ui.html redirects to these
	    	"/swagger-ui/**",
	    	"/v3/api-docs/**"
	    	
	};
	
	@Value("${io.digisic.max.sessions}")
	private String propMaxSessions;
	
	@Bean
	public static HttpSessionEventPublisher httpSessionEventPublisher() {
	    return new HttpSessionEventPublisher();
	}
  
	// static: UserService injects this encoder while this config injects services
	@Bean
	public static BCryptPasswordEncoder encoder() {
		return new BCryptPasswordEncoder();
	}
	
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
		
	@Bean
	@Order(1)
	public SecurityFilterChain apiFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
		
		http.securityMatcher(antMatcher(Constants.URI_API_ALL))
			.csrf(csrf -> csrf.disable())
			.exceptionHandling(e -> e.accessDeniedPage(Constants.URI_API_AUTH))
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(a -> a
				// legacy FilterSecurityInterceptor authorised each request once (no FORWARD/ERROR re-checks)
				.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
				.requestMatchers(antMatcher(HttpMethod.POST, Constants.URI_API_AUTH)).permitAll()
				.requestMatchers(antMatcher(HttpMethod.GET, Constants.URI_API_HEALTHCHECK)).permitAll()
				.anyRequest().hasRole(Patterns.ROLE_API))
			.with(new JwtTokenFilterConfigurer(jwtTokenProvider), c -> {});
		
		return http.build();
	}
	
	@Bean
	public SecurityFilterChain formLoginFilterChain(HttpSecurity http) throws Exception {
		
		Integer iMaxSessions = 1;
		
		try{iMaxSessions = Integer.parseInt(propMaxSessions);}
		catch (NumberFormatException nfe) {}
    	
    	LOG.info("Max Sessions: " + iMaxSessions);
    	
    	final int maxSessions = iMaxSessions;
    	RequestMatcher[] publicMatchers = Arrays.stream(PUBLIC).map(p -> antMatcher(p)).toArray(RequestMatcher[]::new);
    	
    	// legacy saved-request redirects carry no "?continue" marker
    	HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
    	requestCache.setMatchingRequestParameterName(null);
    	
    	http.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.disable())
			.authorizeHttpRequests(a -> a
				.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
				.requestMatchers(publicMatchers).permitAll()
				.anyRequest().hasRole(Patterns.ROLE_USER))
			.formLogin(f -> f
				.failureUrl(Constants.URI_LOGIN_ERR)
				.defaultSuccessUrl(Constants.URI_HOME)
				.loginPage(Constants.URI_LOGIN).permitAll())
			.logout(l -> l
				.logoutRequestMatcher(antMatcher(Constants.URI_LOGOUT))
				.logoutSuccessUrl(Constants.URI_LOGOUT_SUCC)
				.deleteCookies(Constants.COO_JSESSION_ID).permitAll())
			.rememberMe(r -> r
				.key(Constants.COO_REMEBER_ME)
				.tokenValiditySeconds(86400))
			.sessionManagement(s -> s
				.sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
				.maximumSessions(maxSessions).maxSessionsPreventsLogin(true))
			.requestCache(c -> c.requestCache(requestCache))
			.addFilterAfter(new EagerSessionCreationFilter(), ForceEagerSessionCreationFilter.class)
			// Needed to support the h2-console interface
			.headers(h -> h.frameOptions(f -> f.disable()));
    	
    	return http.build();
	}
	
	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth, UserSecurityService userSecurityService) throws Exception {
		auth.userDetailsService(userSecurityService).passwordEncoder(encoder());
	}
}
