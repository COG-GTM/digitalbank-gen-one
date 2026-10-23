package io.digisic.bank.config;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientCertificateConfig {

	private static final Logger LOG = LoggerFactory.getLogger(RestClientCertificateConfig.class);

	// Keystore is mounted at runtime (never packaged); e.g. IO_DIGISIC_BANK_CREDIT_KEYSTORE=file:/usr/local/tomcat/conf/digisic.p12
	@Value("${io.digisic.bank.credit.keystore:${server.ssl.key-store:}}")
	private String keystore;

	@Value("${io.digisic.bank.credit.keystore-password:${DIGISIC_KEYSTORE_PASSWORD:${server.ssl.key-store-password:}}}")
	private String keystorePassword;

	@Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) throws Exception {

		if (keystore == null || keystore.trim().isEmpty()) {
			LOG.warn("No client keystore configured (io.digisic.bank.credit.keystore); Digital Credit calls will not use a client certificate.");
			return builder.build();
		}

		char[] password = keystorePassword.toCharArray();

        SSLContext sslContext = SSLContextBuilder
                .create()
                .loadKeyMaterial(ResourceUtils.getFile(keystore), password, password)
                .loadTrustMaterial(ResourceUtils.getFile(keystore), password)
                .build();
        
        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

        HttpClient client = HttpClients.custom()
        		.setSSLSocketFactory(csf).build();

        return builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(client))
                .build();
    }

}
