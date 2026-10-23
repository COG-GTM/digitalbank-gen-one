package io.digisic.credit.test.junit.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.digisic.credit.repository.UserRepository;
import io.digisic.credit.security.JwtTokenProvider;
import io.digisic.credit.service.UserSecurityService;
import io.jsonwebtoken.Jwts;

/**
 * EM-132: the JWT signing key must be injected from configuration and startup must fail without it.
 */
public class JwtTokenProviderSecretTest {

	private static final String SECRET_PROPERTY = "io.digisic.credit.api.jwt.secret";
	private static final String SECRET = "unit-test-only-signing-key-0123456789abcdef";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfig.class);

	@Test
	public void refusesToStartWithoutSigningKey() {
		runner.run(ctx -> {
			assertThat(ctx).hasFailed();
			assertThat(ctx.getStartupFailure()).hasStackTraceContaining("IO_DIGISIC_CREDIT_API_JWT_SECRET");
		});
	}

	@Test
	public void signsTokensWithInjectedKey() {
		runner.withPropertyValues(SECRET_PROPERTY + "=" + SECRET).run(ctx -> {
			assertThat(ctx).hasNotFailed();
			String token = ctx.getBean(JwtTokenProvider.class).createToken("admin@demo.io", Collections.emptyList());

			String key = Base64.getEncoder().encodeToString(SECRET.getBytes(StandardCharsets.UTF_8));
			assertThat(Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody().getSubject())
					.isEqualTo("admin@demo.io");
		});
	}

	@Configuration
	static class TestConfig {

		@Bean
		public UserRepository userRepository() {
			return Mockito.mock(UserRepository.class);
		}

		@Bean
		public UserSecurityService userSecurityService() {
			return Mockito.mock(UserSecurityService.class);
		}

		@Bean
		public JwtTokenProvider jwtTokenProvider() {
			return new JwtTokenProvider();
		}
	}
}
