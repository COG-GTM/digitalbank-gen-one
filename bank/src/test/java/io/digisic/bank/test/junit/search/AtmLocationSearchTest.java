package io.digisic.bank.test.junit.search;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpStatusCodeException;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.digisic.bank.model.AtmLocation;
import io.digisic.bank.service.SearchService;
import io.digisic.bank.util.Constants;

/**
 * Tests integration between <code>SearchService</code> of Digital Bank and synapsefi ATM locator service.
 * The ATM service is virtualized with WireMock (formerly CodeSV).
 */
@SpringBootTest(classes = SearchService.class)
public class AtmLocationSearchTest {

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
		// SearchService caches its base URL statically; force it to be rebuilt for this test's virtual service
		ReflectionTestUtils.setField(SearchService.class, "apiBaseUrl", null);
	}

	/**
	 * Validate a positive result returned from the ATM location service.
	 */
	@Test
	public void positiveResultTest() throws Exception {

		final String zipcode = "94203";
		final int expResultSize = 2;

		atmService.stubFor(get(urlPathEqualTo(Constants.APP_ATM_URI_API_BASE))
				.withQueryParam("zip", equalTo(zipcode))
				.willReturn(okJson(POSITIVE_RESULTS_OK)));

		List<AtmLocation> locations = searchService.searchATMLocations(zipcode);

		Assertions.assertEquals(expResultSize, locations.size());
	}

	/**
	 * Validate zero results returned from the ATM location service.
	 */
	@Test
	public void zeroResultTest() throws Exception {

		final String zipcode = "12345";

		atmService.stubFor(get(urlPathEqualTo(Constants.APP_ATM_URI_API_BASE))
				.withQueryParam("zip", equalTo(zipcode))
				.willReturn(okJson(ZERO_RESULTS_OK)));

		List<AtmLocation> locations = searchService.searchATMLocations(zipcode);

		Assertions.assertTrue(locations.isEmpty());
	}

	/**
	 * Validate the ATM location service being unavailable.
	 */
	@Test
	public void unavilableServiceTest() {

		final String zipcode = "94203";

		atmService.stubFor(get(urlPathEqualTo(Constants.APP_ATM_URI_API_BASE))
				.withQueryParam("zip", equalTo(zipcode))
				.willReturn(notFound()));

		Assertions.assertThrows(HttpStatusCodeException.class, () -> {
			searchService.searchATMLocations(zipcode);
		});
	}

	private static final String ZERO_RESULTS_OK = 	"{\n" + "    \"atms\": [],\n" +
													"    \"atms_count\": 0,\n" + "    \"error_code\": \"0\",\n" +
													"    \"http_code\": \"200\",\n" + "    \"limit\": 20,\n" +
													"    \"page\": 1,\n" + "    \"page_count\": 0,\n" + "    \"success\": true\n"
													+ "}";
	 

    private static final String POSITIVE_RESULTS_OK = 	"{\n" +
											            "    \"atms\": [\n" +
											            "        {\n" +
											            "            \"atmLocation\": {\n" +
											            "                \"address\": {\n" +
											            "                    \"city\": \"San Francisco\",\n" +
											            "                    \"country\": \"USA\",\n" +
											            "                    \"postalCode\": \"94114\",\n" +
											            "                    \"state\": \"CA\",\n" +
											            "                    \"street\": \"443 Castro Street\"\n" +
											            "                },\n" +
											            "                \"coordinates\": {\n" +
											            "                    \"latitude\": 37.761746,\n" +
											            "                    \"longitude\": -122.435024\n" +
											            "                },\n" +
											            "                \"id\": \"818850\",\n" +
											            "                \"isAvailable24Hours\": true,\n" +
											            "                \"isDepositAvailable\": true,\n" +
											            "                \"isHandicappedAccessible\": false,\n" +
											            "                \"isOffPremise\": false,\n" +
											            "                \"isSeasonal\": false,\n" +
											            "                \"languageType\": null,\n" +
											            "                \"locationDescription\": \"US BANK CASTRO\",\n" +
											            "                \"logoName\": \"PAS\",\n" +
											            "                \"name\": \"U.S. Bank Castro\"\n" +
											            "            },\n" +
											            "            \"distance\": 0.4101105159902258\n" +
											            "        },\n" +
											            "        {\n" +
											            "            \"atmLocation\": {\n" +
											            "                \"address\": {\n" +
											            "                    \"city\": \"San Francisco\",\n" +
											            "                    \"country\": \"USA\",\n" +
											            "                    \"postalCode\": \"94110\",\n" +
											            "                    \"state\": \"CA\",\n" +
											            "                    \"street\": \"2601 Mission Street\"\n" +
											            "                },\n" +
											            "                \"coordinates\": {\n" +
											            "                    \"latitude\": 37.755035,\n" +
											            "                    \"longitude\": -122.41803\n" +
											            "                },\n" +
											            "                \"id\": \"819134\",\n" +
											            "                \"isAvailable24Hours\": true,\n" +
											            "                \"isDepositAvailable\": true,\n" +
											            "                \"isHandicappedAccessible\": false,\n" +
											            "                \"isOffPremise\": false,\n" +
											            "                \"isSeasonal\": false,\n" +
											            "                \"languageType\": null,\n" +
											            "                \"locationDescription\": \"US BANK 22ND & MISSION\",\n" +
											            "                \"logoName\": \"PAS\",\n" +
											            "                \"name\": \"U.S. Bank 22nd & Mission Street\"\n" +
											            "            },\n" +
											            "            \"distance\": 0.7987523536486233\n" +
											            "        }\n" +
											            "    ],\n" +
											            "    \"atms_count\": 2,\n" +
											            "    \"error_code\": \"0\",\n" +
											            "    \"http_code\": \"200\",\n" +
											            "    \"limit\": 20,\n" +
											            "    \"page\": 1,\n" +
											            "    \"page_count\": 1,\n" +
											            "    \"success\": true\n" +
											            "}";
}
