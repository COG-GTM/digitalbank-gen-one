package io.digisic.bank.test.serenity.common;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.digisic.bank.test.support.VirtualServiceFixtures;

/**
 * Serves the recorded external-service fixtures (spec/**{@literal /}vs) on {@code it.mock.port} for the
 * application started by the build (its io.digisic.bank.atm.* properties point here). Replaces CodeSV.
 * Skipped when {@code it.mock.port} is not set, e.g. when running against an externally managed app.
 */
public class ExternalServicesMock {

	private static WireMockServer server;

	@BeforeAll
	public static void startExternalServices() {
		String port = System.getProperty("it.mock.port");
		if (port == null || port.isBlank() || server != null) {
			return;
		}
		server = new WireMockServer(options().bindAddress("127.0.0.1").port(Integer.parseInt(port.trim())));
		server.start();
		VirtualServiceFixtures.register(server, "spec");
	}

	@AfterAll
	public static void stopExternalServices() {
		if (server != null) {
			server.stop();
			server = null;
		}
	}
}
