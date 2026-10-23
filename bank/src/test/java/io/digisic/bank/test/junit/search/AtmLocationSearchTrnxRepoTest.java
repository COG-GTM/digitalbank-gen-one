package io.digisic.bank.test.junit.search;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpStatusCodeException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.digisic.bank.model.AtmLocation;
import io.digisic.bank.service.SearchService;
import io.digisic.bank.test.support.VirtualServiceFixtures;
import io.digisic.bank.util.Constants;

/**
 * Tests <code>SearchService</code> against the recorded ATM search transactions in
 * <code>spec/synapse/vs/atmsearch</code> (formerly a CodeSV / BlazeMeter transaction repository).
 */
@SpringBootTest(classes = SearchService.class)
public class AtmLocationSearchTrnxRepoTest {

	private static final String TRANSACTION_REPO = "spec/synapse/vs/atmsearch";

	@RegisterExtension
	static WireMockExtension atmService = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@Autowired
	private SearchService searchService;

	@DynamicPropertySource
	static void atmServiceProperties(DynamicPropertyRegistry registry) {
		registry.add(Constants.APP_ATM_PROTOCOL, () -> "http");
		registry.add(Constants.APP_ATM_HOST, () -> "localhost");
		registry.add(Constants.APP_ATM_PORT, atmService::getPort);
	}

	@BeforeAll
	static void resetServiceUrl() {
		ReflectionTestUtils.setField(SearchService.class, "apiBaseUrl", null);
	}

	@BeforeEach
	void loadTransactions() {
		// WireMockExtension resets stubs before each test
		Assertions.assertEquals(3, VirtualServiceFixtures.register(atmService, TRANSACTION_REPO));
	}

	@Test
	public void positiveResultTest() throws Exception {

		final String zipcode = "11749";
		final int expResultSize = new ObjectMapper()
				.readTree(VirtualServiceFixtures.responseBody(TRANSACTION_REPO + "/positive/AtmSearch_PositiveResults-rsp.txt"))
				.get("atms").size();

		List<AtmLocation> locations = searchService.searchATMLocations(zipcode);

		Assertions.assertTrue(expResultSize > 0);
		Assertions.assertEquals(expResultSize, locations.size());
	}

	@Test
	public void zeroResultTest() throws Exception {

		final String zipcode = "12345";

		List<AtmLocation> locations = searchService.searchATMLocations(zipcode);

		Assertions.assertTrue(locations.isEmpty());
	}

	@Test
	public void unavilableServiceTest() {

		final String zipcode = "54321";

		Assertions.assertThrows(HttpStatusCodeException.class, () -> {
			searchService.searchATMLocations(zipcode);
		});
	}
}
