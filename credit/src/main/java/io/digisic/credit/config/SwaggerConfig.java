package io.digisic.credit.config;

import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {
	
	private static final String JWT_SCHEME = "bearerAuth";
	private static final String API_PATHS = "/api/**";
	
	@Bean
	public GroupedOpenApi api() {
		return GroupedOpenApi.builder()
				.group("api")
				.packagesToScan("io.digisic.credit.controller")
				.pathsToMatch(API_PATHS)
				.build();
	}
	
	@Bean
	public OpenAPI apiInfo() {
		return new OpenAPI()
				.info(new Info()
						.title("Digital Credit API")
						.description("Digital Credit API provides Administration and User functionality through API Endpoints.")
						.termsOfService("Terms of Service")
						.contact(new Contact()
								.name("Digital Credit")
								.url("www.demo.io")
								.email("digitalcredit@demo.io")))
				.components(new Components()
						.addSecuritySchemes(JWT_SCHEME, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME));
	}

}
