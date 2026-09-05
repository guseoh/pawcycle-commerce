package com.pawcycle.backend.catalog.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.application.CatalogManifestImportException;
import com.pawcycle.backend.catalog.application.CustomerCatalogImportService;
import com.pawcycle.backend.catalog.maintenance.persistence.CustomerCatalogImportPersistence;
import com.pawcycle.backend.catalog.maintenance.persistence.DemoCatalogImportPersistence;
import com.pawcycle.backend.catalog.application.CustomerCatalogImportOperation;
import com.pawcycle.backend.catalog.application.CustomerCatalogImportResult;
import com.pawcycle.backend.catalog.application.CustomerCatalogSupplementImportResult;
import com.pawcycle.backend.catalog.application.DemoCatalogImportOperation;
import com.pawcycle.backend.catalog.application.DemoCatalogImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionDemoCatalogImportConfigurationTests {

  private final DemoCatalogImportPersistence demoImportService =
      mock(DemoCatalogImportPersistence.class);
  private final CustomerCatalogImportService customerImportService =
      mock(CustomerCatalogImportService.class);
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(ProductionDemoCatalogImportConfiguration.class)
          .withBean(DemoCatalogImportPersistence.class, () -> demoImportService)
          .withBean(CustomerCatalogImportService.class, () -> customerImportService);

  @Test
  void productionDoesNotCreateRunnerWithoutExplicitEnablement() {
    contextRunner
        .withPropertyValues("spring.profiles.active=production")
        .run(context -> assertThat(context).doesNotHaveBean("productionDemoCatalogImportRunner"));
  }

  @Test
  void validateModeDefaultsToDemoAndUsesTheManifestLocation() throws Exception {
    DemoCatalogImportResult result = demoResult(DemoCatalogImportOperation.VALIDATE);
    when(demoImportService.validate("classpath:catalog/demo-catalog.json")).thenReturn(result);

    contextRunner
        .withPropertyValues(
            "spring.profiles.active=production",
            "pawcycle.catalog.manifest-import.enabled=true",
            "pawcycle.catalog.manifest-import.mode=validate")
        .run(
            context -> {
              ApplicationRunner runner =
                  context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
              runner.run(new DefaultApplicationArguments());
              verify(demoImportService).validate("classpath:catalog/demo-catalog.json");
              verify(customerImportService, never()).validate();
              assertThat(context.getBean(ProductionDemoCatalogImportResultHolder.class).summary())
                  .isEqualTo(result.summary());
            });
  }

  @Test
  void customerValidateUsesCanonicalCustomerCatalogService() throws Exception {
    CustomerCatalogImportResult result =
        customerResult(CustomerCatalogImportOperation.VALIDATE);
    when(customerImportService.validate()).thenReturn(result);

    contextRunner
        .withPropertyValues(
            "spring.profiles.active=production",
            "pawcycle.catalog.manifest-import.enabled=true",
            "pawcycle.catalog.manifest-import.target=customer",
            "pawcycle.catalog.manifest-import.mode=validate")
        .run(
            context -> {
              ApplicationRunner runner =
                  context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
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
        .run(
            context -> {
              ApplicationRunner runner =
                  context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
              assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                  .isInstanceOf(CatalogManifestImportException.class)
                  .hasMessageContaining("confirmation");
              verify(customerImportService, never()).apply();
            });
  }

  @Test
  void confirmedCustomerApplyUsesCanonicalCustomerCatalogService() throws Exception {
    CustomerCatalogImportResult result =
        customerResult(CustomerCatalogImportOperation.APPLY);
    when(customerImportService.apply()).thenReturn(result);

    contextRunner
        .withPropertyValues(
            "spring.profiles.active=production",
            "pawcycle.catalog.manifest-import.enabled=true",
            "pawcycle.catalog.manifest-import.target=customer",
            "pawcycle.catalog.manifest-import.mode=apply",
            "pawcycle.catalog.manifest-import.confirm-apply=true")
        .run(
            context -> {
              ApplicationRunner runner =
                  context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
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
        .run(
            context -> {
              ApplicationRunner runner =
                  context.getBean("productionDemoCatalogImportRunner", ApplicationRunner.class);
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

  private DemoCatalogImportResult demoResult(DemoCatalogImportOperation operation) {
    return new DemoCatalogImportResult(operation, 4, 32, 42, 42, 6, null);
  }

  private CustomerCatalogImportResult customerResult(
      CustomerCatalogImportOperation operation) {
    DemoCatalogImportOperation baselineOperation =
        operation == CustomerCatalogImportOperation.APPLY
            ? DemoCatalogImportOperation.APPLY
            : DemoCatalogImportOperation.VALIDATE;
    return new CustomerCatalogImportResult(
        demoResult(baselineOperation),
        new CustomerCatalogSupplementImportResult(operation, 9, 23, 68, 124, 0, 0, 0, 0, 0));
  }
}
