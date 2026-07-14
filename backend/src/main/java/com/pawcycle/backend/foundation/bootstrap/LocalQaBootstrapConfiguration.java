package com.pawcycle.backend.foundation.bootstrap;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local-integration & !test & !production & !prod")
@ConditionalOnProperty(
		prefix = "pawcycle.local-qa-bootstrap",
		name = "enabled",
		havingValue = "true")
@EnableConfigurationProperties(LocalQaBootstrapProperties.class)
public class LocalQaBootstrapConfiguration {

	@Bean
	ApplicationRunner localQaBootstrapRunner(
			LocalQaBootstrapProperties properties,
			LocalQaBootstrapService bootstrapService) {
		return arguments -> bootstrapService.bootstrap(
				properties.email(),
				properties.password(),
				properties.resetSubscriptions());
	}
}
