package io.digisic.bank.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import io.digisic.bank.util.Constants;
import io.swagger.v3.oas.annotations.Hidden;

/*
 * Legacy springfox served the API description at /v2/api-docs; springdoc serves it at
 * /v3/api-docs. Keep the legacy URL available by forwarding to the springdoc document.
 */
@Hidden
@Controller
public class ApiDocsController {

	@GetMapping(Constants.URI_SWAGGER_V2)
	public String legacyApiDocs() {
		return "forward:/v3/api-docs";
	}
}
