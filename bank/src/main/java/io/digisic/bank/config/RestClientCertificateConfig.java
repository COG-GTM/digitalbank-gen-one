package io.digisic.bank.config;

import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientCertificateConfig {
	
	private static String DIGITAL_CREDIT_KEYSTORE="keystore/digisic.p12";
	private static String DIGITAL_CREDIT_KEYSTORE_PASS="digisic";
	
	@Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) throws Exception {

		// Loaded as a stream so it also works from inside the executable jar
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream in = new ClassPathResource(DIGITAL_CREDIT_KEYSTORE).getInputStream()) {
			keyStore.load(in, DIGITAL_CREDIT_KEYSTORE_PASS.toCharArray());
		}
		
        SSLContext sslContext = SSLContextBuilder
                .create()
                .loadKeyMaterial(keyStore, DIGITAL_CREDIT_KEYSTORE_PASS.toCharArray())
                .loadTrustMaterial(keyStore, null)
                .build();
        
        HttpClient client = HttpClients.custom()
        		.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
        			.setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
        				.setSslContext(sslContext)
        				.setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
        				.build())
        			.build())
        		.build();

        return builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(client))
                .build();
    }

}
