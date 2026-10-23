package io.digisic.bank.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * Spring 5.1 matched "/api/v1/user/" to "/api/v1/user"; Spring 6 disabled trailing-slash
 * matching by default. Re-enabled so the legacy URLs keep resolving.
 */
@Configuration
public class LegacyPathMatchConfig implements WebMvcConfigurer {

	@Override
	@SuppressWarnings("deprecation")
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.setUseTrailingSlashMatch(true);
	}
}
