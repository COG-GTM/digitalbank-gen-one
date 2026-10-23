package io.digisic.bank.test.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.tomakehurst.wiremock.junit.Stubbing;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

/**
 * Registers the recorded request/response pairs under {@code spec/**}{@code /vs} (formerly replayed by CodeSV)
 * as WireMock stubs. A pair is {@code <name>-req.txt} (raw HTTP request) and {@code <name>-rsp.txt} (raw HTTP response).
 */
public final class VirtualServiceFixtures {

	private static final Set<String> SKIPPED_HEADERS = Set.of("content-length", "transfer-encoding", "connection", "status");

	private VirtualServiceFixtures() {
	}

	/** Registers every fixture pair found below the given classpath directory; returns the number of stubs. */
	public static int register(Stubbing server, String classpathDir) {
		try (Stream<Path> files = Files.walk(classpathDirectory(classpathDir))) {
			List<Path> requests = files.filter(f -> f.getFileName().toString().endsWith("-req.txt")).sorted().collect(Collectors.toList());
			for (Path req : requests) {
				Path rsp = req.resolveSibling(req.getFileName().toString().replace("-req.txt", "-rsp.txt"));
				register(server, req, rsp);
			}
			return requests.size();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Returns the body of a recorded response, e.g. to derive the expected result of a fixture-driven test. */
	public static String responseBody(String classpathFile) {
		return split(read(classpathDirectory(classpathFile)))[1];
	}

	private static void register(Stubbing server, Path req, Path rsp) {
		String requestLine = read(req).lines().findFirst().orElseThrow().trim();
		String[] parts = requestLine.split(" ");
		String method = parts[0].toUpperCase(Locale.ROOT);
		String url = parts[1];

		String[] response = split(read(rsp));
		String[] head = response[0].split("\r?\n");
		int status = Integer.parseInt(head[0].trim().split(" ")[1]);
		ResponseDefinitionBuilder builder = aResponse().withStatus(status).withBody(response[1]);
		for (int i = 1; i < head.length; i++) {
			int colon = head[i].indexOf(':');
			if (colon <= 0) {
				continue;
			}
			String name = head[i].substring(0, colon).trim();
			if (!SKIPPED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
				builder.withHeader(name, head[i].substring(colon + 1).trim());
			}
		}
		server.stubFor(request(method, urlEqualTo(url)).willReturn(builder));
	}

	private static String[] split(String message) {
		String normalized = message.replace("\r\n", "\n");
		int blank = normalized.indexOf("\n\n");
		if (blank < 0) {
			return new String[] { normalized, "" };
		}
		return new String[] { normalized.substring(0, blank), normalized.substring(blank + 2).trim() };
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path classpathDirectory(String classpathDir) {
		URL url = VirtualServiceFixtures.class.getClassLoader().getResource(classpathDir);
		if (url == null) {
			throw new IllegalArgumentException("Fixture path not found on classpath: " + classpathDir);
		}
		try {
			return Paths.get(url.toURI());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
