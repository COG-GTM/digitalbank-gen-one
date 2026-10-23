package io.digisic.bank.test.junit.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

/**
 * EM-133: developer tooling (Swagger, /manager) stays reachable without login
 * only when the "dev" profile is active.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@ExtendWith(SpringExtension.class)
public class WebSecurityDevProfileTest {

	@Autowired
	private MockMvc mvc;

	@Test
	public void swaggerIsPublicInDevProfile() throws Exception {
		mvc.perform(get("/swagger-ui.html")).andExpect(status().isOk());
		mvc.perform(get("/v2/api-docs")).andExpect(status().isOk());
	}

	@Test
	public void managerIsPublicInDevProfile() throws Exception {
		mvc.perform(get("/manager/promotions")).andExpect(status().isOk());
	}
}
