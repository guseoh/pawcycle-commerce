package com.pawcycle.backend.foundation.bootstrap;

import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local-integration & !test & !production & !prod")
public class LocalCustomerCatalogV3Configuration {
  @Bean
  @ConditionalOnProperty(name = "pawcycle.local-customer-catalog-v3.enabled", havingValue = "true")
  ApplicationRunner localCustomerCatalogV3Runner(
      LocalCustomerCatalogV3FixtureService fixture,
      @Value("${pawcycle.local-demo-catalog.manifest:classpath:catalog/demo-catalog.json}")
          String manifest,
      @Value("${pawcycle.local-demo-catalog.enabled:true}") boolean baselineEnabled) {
    return args -> {
      if (!baselineEnabled
          || !DemoCatalogManifestImportService.DEFAULT_MANIFEST_LOCATION.equals(manifest)) {
        throw new LocalQaBootstrapException(
            "Customer Catalog V3는 기본 Data V1과 함께 실행해야 합니다. V2/custom manifest와 혼용할 수 없습니다.");
      }
      fixture.bootstrap();
    };
  }
}
