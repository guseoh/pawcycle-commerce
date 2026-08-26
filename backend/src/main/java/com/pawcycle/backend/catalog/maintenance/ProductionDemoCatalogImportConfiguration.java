package com.pawcycle.backend.catalog.maintenance;

import com.pawcycle.backend.catalog.application.CatalogManifestImportException;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService.ImportResult;
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
			DemoCatalogManifestImportService importService,
			ProductionDemoCatalogImportResultHolder resultHolder,
			@Value("${pawcycle.catalog.manifest-import.mode:}") String mode,
			@Value("${pawcycle.catalog.manifest-import.confirm-apply:false}") boolean confirmApply,
			@Value("${pawcycle.catalog.manifest-import.manifest:classpath:catalog/demo-catalog.json}") String manifestLocation) {
		return arguments -> {
			ImportResult result = switch (mode.toLowerCase(java.util.Locale.ROOT)) {
				case "validate" -> importService.validate(manifestLocation);
				case "apply" -> {
					if (!confirmApply) {
						throw new CatalogManifestImportException("production catalog import apply confirmation is required");
					}
					yield importService.apply(manifestLocation);
				}
				default -> throw new CatalogManifestImportException("production catalog import mode is invalid");
			};
			resultHolder.set(result);
		};
	}
}
