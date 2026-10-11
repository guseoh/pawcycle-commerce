package com.pawcycle.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pawcycle.backend.subscription.automation.SubscriptionOrderAutomationService;
import com.pawcycle.backend.subscription.migration.LegacySubscriptionMigrationProcessor;
import com.pawcycle.backend.subscription.performance.SubscriptionBurstMeasurementService;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

/** T11's nine JDBC tools: keep dormant beans, constrain actual production-source callers. */
class MaintenanceJdbcIsolationTests {
  private static final String ROOT = "com.pawcycle.backend.";

  @Test
  void nineJdbcOwnersHaveOnlyTheAuditedCallers() {
    Map<String, Set<String>> callers = Map.of(
        "catalog.maintenance.persistence.CustomerCatalogImportPersistence", Set.of(
            "catalog.application.CustomerCatalogImportService", "foundation.bootstrap.LocalCustomerCatalogV3FixtureService"),
        "catalog.maintenance.persistence.CustomerCatalogRealismCorrectionPersistence", Set.of(
            "catalog.application.CustomerCatalogImportService", "catalog.maintenance.persistence.CustomerCatalogImportPersistence",
            "catalog.maintenance.persistence.DemoCatalogImportPersistence"),
        "catalog.maintenance.persistence.DemoCatalogImportPersistence", Set.of(
            "catalog.application.CustomerCatalogImportService", "catalog.maintenance.ProductionDemoCatalogImportConfiguration",
            "foundation.bootstrap.LocalCommerceDemoFixtureService"),
        "catalog.maintenance.persistence.ProductDetailSectionFixturePersistence", Set.of(
            "foundation.bootstrap.LocalCommerceDemoFixtureService"),
        "foundation.bootstrap.LocalCustomerCatalogV3FixtureService", Set.of("foundation.bootstrap.LocalCustomerCatalogV3Configuration"),
        "foundation.bootstrap.LocalQaBootstrapService", Set.of("foundation.bootstrap.LocalQaBootstrapConfiguration"),
        "foundation.bootstrap.LocalQaSubscriptionFixtureService", Set.of("foundation.bootstrap.LocalQaBootstrapConfiguration"),
        "subscription.migration.LegacySubscriptionMigrationProcessor", Set.of(),
        "subscription.performance.SubscriptionBurstMeasurementService", Set.of("subscription.performance.SubscriptionBurstMeasurementController"));
    var classes = new ClassFileImporter()
        .withImportOption(new com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests())
        .importPackages(ROOT);
    for (var entry : callers.entrySet()) {
      String owner = ROOT + entry.getKey();
      var actual = classes.get(owner).getDirectDependenciesToSelf().stream()
          .map(dependency -> dependency.getOriginClass().getName())
          .filter(name -> name.startsWith(ROOT) && !name.equals(owner) && !name.startsWith(owner + "$"))
          .map(name -> name.substring(ROOT.length())).collect(java.util.stream.Collectors.toSet());
      assertThat(actual).as("audited callers of %s", owner).containsExactlyInAnyOrderElementsOf(entry.getValue());
    }
  }

  @Test
  void measurementHttpAndServiceRequireMeasurementProfileAndRejectProductionEvenWhenMixed() throws Exception {
    Class<?> controller = Class.forName(ROOT + "subscription.performance.SubscriptionBurstMeasurementController");
    var runner = new ApplicationContextRunner()
        .withUserConfiguration(SubscriptionBurstMeasurementService.class, controller)
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .withBean(SubscriptionOrderAutomationService.class, () -> mock(SubscriptionOrderAutomationService.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withPropertyValues("pawcycle.subscription-burst-measurement.workload-start-marker-path=unused",
            "pawcycle.subscription-burst-measurement.run-armed=true");
    for (String profile : java.util.List.of("default", "test", "production", "prod",
        "subscription-burst-measurement,production", "subscription-burst-measurement,prod")) {
      runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
        assertThat(context).doesNotHaveBean(SubscriptionBurstMeasurementService.class).doesNotHaveBean(controller);
      });
    }
    runner.withPropertyValues("spring.profiles.active=subscription-burst-measurement").run(context -> {
      assertThat(context).hasSingleBean(SubscriptionBurstMeasurementService.class).hasSingleBean(controller);
      // Spring initializes JdbcTemplate; initialization is not measurement SQL execution.
      assertThat(org.mockito.Mockito.mockingDetails(context.getBean(JdbcTemplate.class)).getInvocations())
          .allMatch(invocation -> invocation.getMethod().getName().equals("afterPropertiesSet"));
      verifyNoInteractions(context.getBean(SubscriptionOrderAutomationService.class));
    });
  }

  @Test
  void migrationRejectsMissingSourceWriteFreezeBeforeAnyJdbcAccess() {
    var jdbc = mock(JdbcTemplate.class);
    assertThatThrownBy(() -> new LegacySubscriptionMigrationProcessor(jdbc).migrateAfterSourceWriteFreeze(false))
        .isInstanceOf(IllegalStateException.class).hasMessage("legacy source writes must be frozen before migration");
    verifyNoInteractions(jdbc);
  }
}
