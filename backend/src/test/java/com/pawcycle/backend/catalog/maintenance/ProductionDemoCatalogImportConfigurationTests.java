package com.pawcycle.backend.catalog.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService.ImportResult;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionDemoCatalogImportConfigurationTests {

	private final DemoCatalogManifestImportService importService = mock(DemoCatalogManifestImportService.class);
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(ProductionDemoCatalogImportConfiguration.class)
			.withBean(DemoCatalogManifestImportService.class, () -> importService);

	@Test
	void productionDoesNotCreateRunnerWithoutExplicitEnablement() {
		contextRunner
				.withPropertyValues("spring.profiles.active=production")
				.run(context -> assertThat(context).doesNotHaveBean("productionDemoCatalogImportRunner"));
	}

	@Test
	void validateModeIsExplicitAndUsesTheManifestLocation() throws Exception {
		ImportResult result = new ImportResult(Operation.VALIDATE, 4, 32, 42, 42, 6, null);
		when(importService.validate("classpath:catalog/demo-catalog.json")).thenReturn(result);

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=production",
						"pawcycle.catalog.manifest-import.enabled=true",
						"pawcycle.catalog.manifest-import.mode=validate")
				.run(context -> {
					ApplicationRunner runner = context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
					runner.run(new DefaultApplicationArguments());
					verify(importService).validate("classpath:catalog/demo-catalog.json");
					assertThat(context.getBean(ProductionDemoCatalogImportResultHolder.class).result()).isSameAs(result);
				});
	}

	@Test
	void productionProfileStillBlocksCommandConfigurationInLocalProfile() {
		contextRunner
				.withPropertyValues(
						"spring.profiles.active=local-integration",
						"pawcycle.catalog.manifest-import.enabled=true",
						"pawcycle.catalog.manifest-import.mode=apply")
				.run(context -> assertThat(context).doesNotHaveBean("productionDemoCatalogImportRunner"));
	}
}
