package io.digisic.bank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.digisic.bank.util.Constants;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {
	
	private static String JWT_TOKEN_REF	= "Bearer %token";
	
	// Paths/packages are scoped via springdoc.paths-to-match and springdoc.packages-to-scan
	@Bean
	public OpenAPI api() {
		
		return new OpenAPI()
			.info(apiInfo())
			.components(new Components().addSecuritySchemes(JWT_TOKEN_REF,
				new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER)
					.name(Constants.API_AUTH_HEADER)))
			.addSecurityItem(new SecurityRequirement().addList(JWT_TOKEN_REF));
	}
	
	private Info apiInfo() {
		
		return new Info()
			.title("Digital Bank API")
			.description("Digial Bank API provides Administration and User functionality through API Endpoints.")
			.version("API TOS")
			.termsOfService("Terms of Service")
			.contact(new Contact()
				.name("Digital Bank")
				.url("www.demo.io")
				.email("digitalbank@demo.io"))
			.license(new License()
				.name("License of API")
				.url("API License URL"));
	}
}
