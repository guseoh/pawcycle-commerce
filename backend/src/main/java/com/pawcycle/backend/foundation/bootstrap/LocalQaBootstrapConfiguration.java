package com.pawcycle.backend.foundation.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local-integration & !test & !production & !prod")
@EnableConfigurationProperties(LocalQaBootstrapProperties.class)
public class LocalQaBootstrapConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "pawcycle.local-qa-bootstrap",
      name = "enabled",
      havingValue = "true")
  ApplicationRunner localQaBootstrapRunner(
      LocalQaBootstrapProperties properties,
      LocalQaBootstrapService bootstrapService,
      LocalQaMvp2FixtureService mvp2FixtureService,
      @Value("${pawcycle.local-customer-catalog-v3.enabled:false}")
          boolean customerCatalogV3Enabled) {
    return arguments -> {
      bootstrapService.bootstrap(
          properties.email(),
          properties.password(),
          properties.resetSubscriptions(),
          !customerCatalogV3Enabled);
      mvp2FixtureService.bootstrap();
    };
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "pawcycle.local-demo-catalog",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  ApplicationRunner localDemoCatalogBootstrapRunner(
      LocalCommerceDemoFixtureService commerceDemoFixtureService) {
    return arguments -> commerceDemoFixtureService.bootstrap();
  }
}
