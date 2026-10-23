package io.digisic.bank.jms;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/*
 * Spring Boot 3 dropped spring.artemis.host / spring.artemis.port in favour of
 * spring.artemis.broker-url. The deployment descriptors still configure the broker
 * with SPRING_ARTEMIS_HOST / SPRING_ARTEMIS_PORT, so map them onto broker-url when
 * no broker-url is configured explicitly.
 */
@Configuration(proxyBeanMethods = false)
public class ArtemisLegacyPropertiesConfig {

	static final String HOST_PROPERTY = "spring.artemis.host";
	static final String PORT_PROPERTY = "spring.artemis.port";
	static final String DEFAULT_HOST = "localhost";
	static final String DEFAULT_PORT = "61616";

	@Bean
	static BeanPostProcessor artemisLegacyHostPortPostProcessor(Environment environment) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
				if (bean instanceof ArtemisProperties properties) {
					String brokerUrl = legacyBrokerUrl(properties.getBrokerUrl(), environment.getProperty(HOST_PROPERTY),
							environment.getProperty(PORT_PROPERTY));
					if (brokerUrl != null) {
						properties.setBrokerUrl(brokerUrl);
					}
				}
				return bean;
			}
		};
	}

	static String legacyBrokerUrl(String brokerUrl, String host, String port) {
		if (StringUtils.hasText(brokerUrl) || (!StringUtils.hasText(host) && !StringUtils.hasText(port))) {
			return null;
		}
		return "tcp://" + uriHost(StringUtils.hasText(host) ? host.trim() : DEFAULT_HOST) + ":"
				+ (StringUtils.hasText(port) ? port.trim() : DEFAULT_PORT);
	}

	static String uriHost(String host) {
		return host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
	}

}
