package io.digisic.bank.config;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
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
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import io.digisic.bank.security.JwtTokenFilterConfigurer;
import io.digisic.bank.security.JwtTokenProvider;
import io.digisic.bank.service.UserSecurityService;
import io.digisic.bank.util.Constants;
import io.digisic.bank.util.Patterns;

@Configuration
@EnableWebSecurity(debug=false)
@EnableGlobalMethodSecurity(prePostEnabled=true)
public class MultiHttpSecurityConfig {

	@Autowired
	private UserSecurityService userSecurityService;
	
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
	    	Constants.URI_FAVICON_RES
	    	
	};
	
	@Bean
	public HttpSessionEventPublisher httpSessionEventPublisher() {
	    return new HttpSessionEventPublisher();
	}
  
	@Bean
	public BCryptPasswordEncoder encoder() {
		return new BCryptPasswordEncoder();
	}
		
	@Configuration
    @Order(1)                                                        
    public static class ApiWebSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {
		
		@Autowired
		private JwtTokenProvider jwtTokenProvider;
		
		@Bean
		@Override
		public AuthenticationManager authenticationManagerBean() throws Exception {
	       return super.authenticationManagerBean();
		}
		
		
        protected void configure(HttpSecurity http) throws Exception {
        	
        	http.csrf().disable()
        		.exceptionHandling().accessDeniedPage(Constants.URI_API_AUTH)
        		.and()
        		.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        		.and()
        		.antMatcher(Constants.URI_API_ALL)
        		.authorizeRequests()
        			.antMatchers(HttpMethod.POST, Constants.URI_API_AUTH).permitAll()
        			.antMatchers(HttpMethod.GET, Constants.URI_API_HEALTHCHECK).permitAll()
        			.anyRequest()
        			.hasRole(Patterns.ROLE_API)
        			.and()
        			.apply(new JwtTokenFilterConfigurer(jwtTokenProvider));
   
        }
    }
	
	@Configuration                                                   
    public static class FormLoginWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
		
		private static final Logger LOG = LoggerFactory.getLogger(FormLoginWebSecurityConfigurerAdapter.class);
		
		@Value("${io.digisic.max.sessions}")
		private String propMaxSessions;
		
		@Value("${io.digisic.bank.security.remember-me.key:}")
		private String propRememberMeKey;
		
		/*
		 * Remember-me signing key: configured per install, otherwise a random key generated at startup.
		 */
		private String rememberMeKey() {
			
			if (propRememberMeKey != null && !propRememberMeKey.trim().isEmpty()) {
				return propRememberMeKey.trim();
			}
			
			LOG.warn("io.digisic.bank.security.remember-me.key not set; using a random key (remember-me cookies will not survive a restart).");
			
			byte[] key = new byte[32];
			new SecureRandom().nextBytes(key);
			return Base64.getEncoder().encodeToString(key);
		}
		
		private ExceptionMappingAuthenticationFailureHandler loginFailureHandler() {
			
			ExceptionMappingAuthenticationFailureHandler handler = new ExceptionMappingAuthenticationFailureHandler();
			handler.setDefaultFailureUrl(Constants.URI_LOGIN_ERR);
			handler.setExceptionMappings(Collections.singletonMap(LockedException.class.getName(), Constants.URI_LOGIN_LOCKED));
			return handler;
		}
		
		@Override
        protected void configure(HttpSecurity http) throws Exception {
			
			Integer iMaxSessions = 1;
			
			try{iMaxSessions = Integer.parseInt(propMaxSessions);}
			catch (NumberFormatException nfe) {}
        	
        	LOG.info("Max Sessions: " + iMaxSessions);
        	
        	http.csrf().disable()
				.cors().disable()
				.authorizeRequests()
					.antMatchers(PUBLIC).permitAll()
					.antMatchers(Constants.URI_LOGIN).permitAll()
					// Payments always need a full (password) login; a remember-me cookie alone is not enough
					.antMatchers(Constants.URI_ACCOUNT + Constants.URI_WITHDRAW,
								 Constants.URI_ACCOUNT + Constants.URI_XFER_BETWEEN)
						.access(Constants.HAS_ROLE_USER + " and isFullyAuthenticated()")
					.anyRequest().hasRole(Patterns.ROLE_USER)
					.and()
				.formLogin()
					.failureHandler(loginFailureHandler())
					.defaultSuccessUrl(Constants.URI_HOME)
					.loginPage(Constants.URI_LOGIN).permitAll()
					.and()
				.logout()
					.logoutRequestMatcher(new AntPathRequestMatcher(Constants.URI_LOGOUT))
					.logoutSuccessUrl(Constants.URI_LOGOUT_SUCC)
					.deleteCookies(Constants.COO_JSESSION_ID).permitAll()
					.and()
				.rememberMe()
					.key(rememberMeKey())
					.tokenValiditySeconds(86400)
					.and()
				.sessionManagement()
					.sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
					.maximumSessions(iMaxSessions).maxSessionsPreventsLogin(true);
		
		
			// Needed to support the h2-console interface
			http.headers().frameOptions().disable();
        }
    }
	
	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(userSecurityService).passwordEncoder(encoder());
	}
}
