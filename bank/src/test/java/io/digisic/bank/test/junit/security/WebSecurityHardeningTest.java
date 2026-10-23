package io.digisic.bank.test.junit.security;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.digisic.bank.model.Account;
import io.digisic.bank.service.AccountService;
import io.digisic.bank.service.SampleDataService;

/**
 * EM-133 / APP-SEC-05: CSRF, clickjacking and public-endpoint hardening of the
 * form-login (cookie session) security chain, with the default (non-dev) profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
public class WebSecurityHardeningTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AccountService accountService;

	private static RequestPostProcessor customer() {
		return user(SampleDataService.SMPL_MALE_EMAIL).roles("USER");
	}

	@Test
	public void transferWithoutCsrfTokenIsForbidden() throws Exception {
		mvc.perform(post("/account/xfer-between").with(customer())
				.param("fromAccount", "1").param("toAccount", "2").param("amount", "1.00"))
			.andExpect(status().isForbidden());
	}

	@Test
	public void accountRenameWithoutCsrfTokenIsForbidden() throws Exception {
		mvc.perform(post("/account/update").with(customer())
				.param("id", "1").param("newName", "Renamed"))
			.andExpect(status().isForbidden());
	}

	@Test
	public void accountRenameWithCsrfHeaderIsAccepted() throws Exception {
		Account account = accountService.getSavingsAccounts().get(0);
		String original = account.getName();
		try {
			mvc.perform(post("/account/update").with(customer()).with(csrf().asHeader())
					.param("id", account.getId().toString()).param("newName", "Renamed via header"))
				.andExpect(status().isOk());
			assertEquals("Renamed via header", accountService.getAccountById(account.getId()).getName());
		} finally {
			Account restore = accountService.getAccountById(account.getId());
			restore.setName(original);
			accountService.updateAccount(restore);
		}
	}

	@Test
	public void accountViewsSendCsrfHeaderWithRenameRequests() throws Exception {
		mvc.perform(get("/account/savings-view").with(customer()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("\"X-XSRF-TOKEN\"")));
		mvc.perform(get("/account/checking-view").with(customer()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("\"X-XSRF-TOKEN\"")));
	}

	@Test
	public void transferFormRendersCsrfToken() throws Exception {
		mvc.perform(get("/account/xfer-between").with(customer()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"_csrf\"")));
	}

	@Test
	public void loginFormRendersCsrfTokenAndLoginWithTokenIsAccepted() throws Exception {
		mvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"_csrf\"")));

		mvc.perform(post("/login").with(csrf())
				.param("username", SampleDataService.SMPL_MALE_EMAIL).param("password", "Demo123!"))
			.andExpect(redirectedUrl("/home"));
	}

	@Test
	public void loginWithoutCsrfTokenIsForbidden() throws Exception {
		mvc.perform(post("/login")
				.param("username", SampleDataService.SMPL_MALE_EMAIL).param("password", "Demo123!"))
			.andExpect(status().isForbidden());
	}

	@Test
	public void responsesIncludeXFrameOptions() throws Exception {
		mvc.perform(get("/login"))
			.andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
	}

	@Test
	public void h2ConsoleRequiresAuthentication() throws Exception {
		assertRedirectsToLogin("/h2-console/");
	}

	@Test
	public void swaggerRequiresAuthentication() throws Exception {
		assertRedirectsToLogin("/swagger-ui.html");
		assertRedirectsToLogin("/v2/api-docs");
		assertRedirectsToLogin("/swagger-resources");
	}

	@Test
	public void managerRequiresAuthentication() throws Exception {
		assertRedirectsToLogin("/manager/promotions");
	}

	private void assertRedirectsToLogin(String uri) throws Exception {
		mvc.perform(get(uri))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern("**/login"));
	}
}
