package com.pawcycle.backend.catalog.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.application.CatalogManifestImportException;
import com.pawcycle.backend.catalog.application.CustomerCatalogImportService;
import com.pawcycle.backend.catalog.application.CustomerCatalogV3ImportService;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService.ImportResult;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionDemoCatalogImportConfigurationTests {

	private final DemoCatalogManifestImportService demoImportService = mock(DemoCatalogManifestImportService.class);
	private final CustomerCatalogImportService customerImportService = mock(CustomerCatalogImportService.class);
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(ProductionDemoCatalogImportConfiguration.class)
			.withBean(DemoCatalogManifestImportService.class, () -> demoImportService)
			.withBean(CustomerCatalogImportService.class, () -> customerImportService);

	@Test
	void productionDoesNotCreateRunnerWithoutExplicitEnablement() {
		contextRunner
				.withPropertyValues("spring.profiles.active=production")
				.run(context -> assertThat(context).doesNotHaveBean("productionDemoCatalogImportRunner"));
	}

	@Test
	void validateModeDefaultsToDemoAndUsesTheManifestLocation() throws Exception {
		ImportResult result = demoResult(Operation.VALIDATE);
		when(demoImportService.validate("classpath:catalog/demo-catalog.json")).thenReturn(result);

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=production",
						"pawcycle.catalog.manifest-import.enabled=true",
						"pawcycle.catalog.manifest-import.mode=validate")
				.run(context -> {
					ApplicationRunner runner = context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
					runner.run(new DefaultApplicationArguments());
					verify(demoImportService).validate("classpath:catalog/demo-catalog.json");
					verify(customerImportService, never()).validate();
					assertThat(context.getBean(ProductionDemoCatalogImportResultHolder.class).summary())
							.isEqualTo(result.summary());
				});
	}

	@Test
	void customerValidateUsesCanonicalCustomerCatalogService() throws Exception {
		CustomerCatalogImportService.ImportResult result = customerResult(CustomerCatalogV3ImportService.Operation.VALIDATE);
		when(customerImportService.validate()).thenReturn(result);

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=production",
						"pawcycle.catalog.manifest-import.enabled=true",
						"pawcycle.catalog.manifest-import.target=customer",
						"pawcycle.catalog.manifest-import.mode=validate")
				.run(context -> {
					ApplicationRunner runner = context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
					runner.run(new DefaultApplicationArguments());
					verify(customerImportService).validate();
					verify(demoImportService, never()).validate("classpath:catalog/demo-catalog.json");
					assertThat(context.getBean(ProductionDemoCatalogImportResultHolder.class).summary())
							.isEqualTo(result.summary());
				});
	}

	@Test
	void applyModeRequiresConfigurationLevelConfirmation() {
		contextRunner
				.withPropertyValues(
						"spring.profiles.active=production",
						"pawcycle.catalog.manifest-import.enabled=true",
						"pawcycle.catalog.manifest-import.target=customer",
						"pawcycle.catalog.manifest-import.mode=apply")
				.run(context -> {
					ApplicationRunner runner = context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
					assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
							.isInstanceOf(CatalogManifestImportException.class)
							.hasMessageContaining("confirmation");
					verify(customerImportService, never()).apply();
				});
	}

	@Test
	void confirmedCustomerApplyUsesCanonicalCustomerCatalogService() throws Exception {
		CustomerCatalogImportService.ImportResult result = customerResult(CustomerCatalogV3ImportService.Operation.APPLY);
		when(customerImportService.apply()).thenReturn(result);

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=production",
						"pawcycle.catalog.manifest-import.enabled=true",
						"pawcycle.catalog.manifest-import.target=customer",
						"pawcycle.catalog.manifest-import.mode=apply",
						"pawcycle.catalog.manifest-import.confirm-apply=true")
				.run(context -> {
					ApplicationRunner runner = context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
					runner.run(new DefaultApplicationArguments());
					verify(customerImportService).apply();
					assertThat(context.getBean(ProductionDemoCatalogImportResultHolder.class).summary())
							.isEqualTo(result.summary());
				});
	}

	@Test
	void invalidTargetFailsClosed() {
		contextRunner
				.withPropertyValues(
						"spring.profiles.active=production",
						"pawcycle.catalog.manifest-import.enabled=true",
						"pawcycle.catalog.manifest-import.target=unknown",
						"pawcycle.catalog.manifest-import.mode=validate")
				.run(context -> {
					ApplicationRunner runner = context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
					assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
							.isInstanceOf(CatalogManifestImportException.class)
							.hasMessageContaining("target");
				});
	}

	@Test
	void productionProfileStillBlocksCommandConfigurationInLocalProfile() {
		contextRunner
				.withPropertyValues(
						"spring.profiles.active=local-integration",
						"pawcycle.catalog.manifest-import.enabled=true",
						"pawcycle.catalog.manifest-import.target=customer",
						"pawcycle.catalog.manifest-import.mode=apply")
				.run(context -> assertThat(context).doesNotHaveBean("productionDemoCatalogImportRunner"));
	}

	private ImportResult demoResult(Operation operation) {
		return new ImportResult(operation, 4, 32, 42, 42, 6, null);
	}

	private CustomerCatalogImportService.ImportResult customerResult(CustomerCatalogV3ImportService.Operation operation) {
		Operation baselineOperation = operation == CustomerCatalogV3ImportService.Operation.APPLY
				? Operation.APPLY : Operation.VALIDATE;
		return new CustomerCatalogImportService.ImportResult(
				demoResult(baselineOperation),
				new CustomerCatalogV3ImportService.ImportResult(operation, 9, 23, 68, 124, 0, 0, 0, 0, 0));
	}
}
