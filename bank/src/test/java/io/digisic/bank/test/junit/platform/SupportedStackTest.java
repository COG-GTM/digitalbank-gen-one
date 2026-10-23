package io.digisic.bank.test.junit.platform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.test.util.ReflectionTestUtils;

import io.digisic.bank.exception.RestInvalidArguementException;
import io.digisic.bank.model.security.Role;
import io.digisic.bank.security.JwtTokenProvider;

/**
 * EM-140 / ITGC-VULN-14: guards against regressing to the end-of-life Spring Boot 2.1 / Tomcat 9.0.48 stack.
 */
public class SupportedStackTest {

	private static final Pattern DOCKER_BASE_IMAGE =
			Pattern.compile("^FROM tomcat:(9\\.0\\.\\d+)-jdk11-temurin(-[a-z]+)?\\s*$", Pattern.MULTILINE);

	@Test
	public void springBootIsOnLatestJava8CompatibleLine() {
		assertAtLeast("Spring Boot", SpringBootVersion.getVersion(), "2.7.18");
	}

	@Test
	public void springFrameworkIsPatchedForSpring4Shell() {
		assertAtLeast("Spring Framework", SpringVersion.getVersion(), "5.3.39");

		// CVE-2022-22965: data binding must not reach the ClassLoader through Class.getModule()
		BeanWrapperImpl wrapper = new BeanWrapperImpl(new FormBean());
		assertFalse(wrapper.isReadableProperty("class.module.classLoader"),
				"class.module.classLoader must not be bindable");
	}

	@Test
	public void embeddedTomcatIsPatchedAndMatchesPinnedDockerImage() throws IOException {
		String embedded = ServerInfo.getServerNumber().replaceAll("^(\\d+\\.\\d+\\.\\d+).*$", "$1");
		assertAtLeast("Embedded Tomcat", embedded, "9.0.123");

		Path dockerfile = Paths.get(System.getProperty("basedir", "."), "Dockerfile");
		String content = new String(Files.readAllBytes(dockerfile), StandardCharsets.UTF_8);
		Matcher m = DOCKER_BASE_IMAGE.matcher(content);
		assertTrue(m.find(), "bank/Dockerfile must pin a patched tomcat:9.0.x-jdk11-temurin image, found: "
				+ content.split("\\R", 2)[0]);
		assertEquals(embedded, m.group(1), "Docker base image Tomcat must match the embedded Tomcat version");
	}

	@Test
	public void unmaintainedLibrariesAreReplaced() {
		assertThrows(ClassNotFoundException.class,
				() -> Class.forName("springfox.documentation.spring.web.plugins.Docket"), "springfox must be removed");
		assertDoesNotThrow(() -> Class.forName("org.springdoc.core.GroupedOpenApi"));
		assertDoesNotThrow(() -> Class.forName("io.jsonwebtoken.Jwts$SIG"), "jjwt must be 0.12+");
	}

	@Test
	public void jwtRoundTripsAndRejectsForgedTokens() {
		JwtTokenProvider provider = new JwtTokenProvider();
		ReflectionTestUtils.invokeMethod(provider, "init");

		String token = provider.createToken("jsmith@demo.io", Collections.singletonList(new Role("API")));
		assertNotNull(token);
		assertEquals("jsmith@demo.io", provider.getUsername(token));
		assertTrue(provider.validateToken(token));

		String[] parts = token.split("\\.");
		String tampered = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
		assertThrows(RestInvalidArguementException.class, () -> provider.validateToken(tampered));

		String unsignedHeader = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
		String unsigned = unsignedHeader + "." + parts[1] + ".";
		assertThrows(RestInvalidArguementException.class, () -> provider.validateToken(unsigned));
	}

	private static void assertAtLeast(String component, String actual, String minimum) {
		assertNotNull(actual, component + " version not found");
		assertTrue(compare(actual, minimum) >= 0,
				component + " " + actual + " is older than the minimum supported/patched " + minimum);
	}

	private static int compare(String a, String b) {
		String[] x = a.split("[.\\-]");
		String[] y = b.split("[.\\-]");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int xi = i < x.length && x[i].matches("\\d+") ? Integer.parseInt(x[i]) : 0;
			int yi = i < y.length && y[i].matches("\\d+") ? Integer.parseInt(y[i]) : 0;
			if (xi != yi) {
				return Integer.compare(xi, yi);
			}
		}
		return 0;
	}

	public static class FormBean {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
