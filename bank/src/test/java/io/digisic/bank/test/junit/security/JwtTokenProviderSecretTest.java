package io.digisic.bank.test.junit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.digisic.bank.exception.RestInvalidArguementException;
import io.digisic.bank.repository.UserRepository;
import io.digisic.bank.security.JwtTokenProvider;
import io.digisic.bank.service.UserSecurityService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * EM-132: the JWT signing key must be injected from configuration and startup must fail without it.
 */
public class JwtTokenProviderSecretTest {

	private static final String SECRET_PROPERTY = "io.digisic.bank.api.jwt.secret";
	private static final String SECRET = "unit-test-only-signing-key-0123456789abcdef";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfig.class);

	@Test
	public void refusesToStartWithoutSigningKey() {
		runner.run(ctx -> {
			assertThat(ctx).hasFailed();
			assertThat(ctx.getStartupFailure()).hasStackTraceContaining("IO_DIGISIC_BANK_API_JWT_SECRET");
		});
	}

	@Test
	public void refusesToStartWithShortSigningKey() {
		runner.withPropertyValues(SECRET_PROPERTY + "=too-short").run(ctx -> assertThat(ctx).hasFailed());
	}

	@Test
	public void signsTokensWithInjectedKey() {
		runner.withPropertyValues(SECRET_PROPERTY + "=" + SECRET).run(ctx -> {
			assertThat(ctx).hasNotFailed();
			JwtTokenProvider provider = ctx.getBean(JwtTokenProvider.class);

			String token = provider.createToken("admin@demo.io", Collections.emptyList());

			String subject = Jwts.parser().setSigningKey(encode(SECRET)).parseClaimsJws(token).getBody().getSubject();
			assertThat(subject).isEqualTo("admin@demo.io");
			assertThat(provider.validateToken(token)).isTrue();
		});
	}

	@Test
	public void rejectsTokenSignedWithAnotherKey() {
		runner.withPropertyValues(SECRET_PROPERTY + "=" + SECRET).run(ctx -> {
			JwtTokenProvider provider = ctx.getBean(JwtTokenProvider.class);
			String forged = Jwts.builder().setSubject("admin@demo.io")
					.signWith(SignatureAlgorithm.HS256, encode("attacker-chosen-key-0123456789abcdef"))
					.compact();

			assertThrows(RestInvalidArguementException.class, () -> provider.validateToken(forged));
		});
	}

	private static String encode(String key) {
		return Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
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
