package com.pawcycle.backend.catalog.maintenance;

import com.pawcycle.backend.catalog.application.CatalogManifestImportException;
import com.pawcycle.backend.catalog.application.CustomerCatalogImportService;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("production | prod")
@ConditionalOnProperty(prefix = "pawcycle.catalog.manifest-import", name = "enabled", havingValue = "true")
public class ProductionDemoCatalogImportConfiguration {

	@Bean
	ProductionDemoCatalogImportResultHolder productionDemoCatalogImportResultHolder() {
		return new ProductionDemoCatalogImportResultHolder();
	}

	@Bean
	ApplicationRunner productionDemoCatalogImportRunner(
			DemoCatalogManifestImportService demoImportService,
			CustomerCatalogImportService customerImportService,
			ProductionDemoCatalogImportResultHolder resultHolder,
			@Value("${pawcycle.catalog.manifest-import.target:demo}") String target,
			@Value("${pawcycle.catalog.manifest-import.mode:}") String mode,
			@Value("${pawcycle.catalog.manifest-import.confirm-apply:false}") boolean confirmApply,
			@Value("${pawcycle.catalog.manifest-import.manifest:classpath:catalog/demo-catalog.json}") String manifestLocation) {
		return arguments -> {
			String normalizedTarget = target.toLowerCase(Locale.ROOT);
			String normalizedMode = mode.toLowerCase(Locale.ROOT);
			if (!normalizedTarget.equals("demo") && !normalizedTarget.equals("customer")) {
				throw new CatalogManifestImportException("production catalog import target is invalid");
			}
			if (normalizedMode.equals("apply") && !confirmApply) {
				throw new CatalogManifestImportException("production catalog import apply confirmation is required");
			}

			String summary = switch (normalizedTarget) {
				case "demo" -> switch (normalizedMode) {
					case "validate" -> demoImportService.validate(manifestLocation).summary();
					case "apply" -> demoImportService.apply(manifestLocation).summary();
					default -> throw new CatalogManifestImportException("production catalog import mode is invalid");
				};
				case "customer" -> switch (normalizedMode) {
					case "validate" -> customerImportService.validate().summary();
					case "apply" -> customerImportService.apply().summary();
					default -> throw new CatalogManifestImportException("production catalog import mode is invalid");
				};
				default -> throw new CatalogManifestImportException("production catalog import target is invalid");
			};
			resultHolder.set(summary);
		};
	}
}
