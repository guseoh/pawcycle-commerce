package com.pawcycle.backend.subscription.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "pawcycle.subscription-burst-measurement.workload-start-marker-path=${java.io.tmpdir}/pawcycle-perf-ph10-002-test-workload-started.json",
      "pawcycle.subscription-burst-measurement.run-armed=true",
      "pawcycle.subscription-burst-measurement.batch-size=500",
      "pawcycle.subscription-burst-measurement.fixed-delay-ms=15000"
    })
@ActiveProfiles({"test", "subscription-burst-measurement"})
class SubscriptionBurstMeasurementServiceIntegrationTests {

  private static final Path MARKER =
      Path.of(
          System.getProperty("java.io.tmpdir"),
          "pawcycle-perf-ph10-002-test-workload-started.json");

  @Autowired private SubscriptionBurstMeasurementService measurementService;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() throws Exception {
    Files.deleteIfExists(MARKER);
    cleanFixture();
  }

  @AfterEach
  void tearDown() throws Exception {
    cleanFixture();
    Files.deleteIfExists(MARKER);
  }

  @Test
  void smallFixtureUsesValidDomainRowsAndDrainProducesOneOrderPerSchedule() {
    SubscriptionFixtureSummary fixture = measurementService.setup(2);

    assertThat(fixture.cohortSize()).isEqualTo(2);
    assertThat(fixture.initialBacklog()).isEqualTo(2);
    assertThat(fixture.batchSize()).isEqualTo(500);
    assertThat(fixture.fixedDelayMs()).isEqualTo(15_000);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM subscriptions subscription
                JOIN members member ON member.id=subscription.member_id
                JOIN subscription_snapshots snapshot ON snapshot.id=subscription.current_snapshot_id
                JOIN subscription_snapshot_items item ON item.snapshot_id=snapshot.id
                JOIN subscription_schedules schedule ON schedule.subscription_id=subscription.id
                JOIN subscription_shipping_snapshots shipping ON shipping.subscription_id=subscription.id
                JOIN billing_payment_methods billing ON billing.member_id=member.id AND billing.status='ACTIVE'
                WHERE member.email LIKE 'perf-ph10-002-%@synthetic.invalid'
                  AND subscription.status='ACTIVE' AND subscription.runtime_managed=true
                  AND schedule.status='SCHEDULED'
                """,
                Integer.class))
        .isEqualTo(2);

    SubscriptionDrainSummary result = measurementService.drain();

    assertThat(Files.exists(MARKER)).isTrue();
    assertThat(result.initialBacklog()).isEqualTo(2);
    assertThat(result.finalBacklog()).isZero();
    assertThat(result.processed()).isEqualTo(2);
    assertThat(result.created()).isEqualTo(2);
    assertThat(result.failures()).isZero();
    assertThat(result.duplicateOrNoOp()).isZero();
    assertThat(result.databaseOrderCount()).isEqualTo(2);
    assertThat(result.duplicateScheduleOrderCount()).isZero();
    assertThat(result.futureScheduleCount()).isEqualTo(2);
    assertThat(result.harnessFailure()).isFalse();
    assertThat(result.defaultSchedulerBatchSize()).isEqualTo(500);
    assertThat(result.defaultSchedulerFixedDelayMs()).isEqualTo(15_000);
    assertThat(result.defaultSchedulerProjectedTicks()).isEqualTo(1);
  }

  @Test
  void drainRejectsAnExistingMarkerAfterFixtureSetup() throws Exception {
    measurementService.setup(1);
    Files.writeString(MARKER, "{\"workloadInvocationStarted\":true}");

    assertThatThrownBy(measurementService::drain)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("workload-start marker");
  }

  @Test
  void setupRejectsExistingFixtureAndOutOfRangeCohorts() {
    measurementService.setup(1);

    assertThatThrownBy(() -> measurementService.setup(1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fixture already exists");
    assertThatThrownBy(() -> measurementService.setup(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> measurementService.setup(10_001))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void drainRejectsNonFixtureEligibleCandidateBeforeWritingMarker() {
    measurementService.setup(1);
    try {
      jdbc.update(
          "UPDATE members SET email='nonfixture-perf-ph10-002@synthetic.invalid' WHERE"
              + " email='perf-ph10-002-0@synthetic.invalid'");

      assertThatThrownBy(measurementService::drain)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("outside the synthetic fixture scope");
      assertThat(Files.exists(MARKER)).isFalse();
    } finally {
      jdbc.update(
          "UPDATE members SET email='perf-ph10-002-0@synthetic.invalid' WHERE"
              + " email='nonfixture-perf-ph10-002@synthetic.invalid'");
    }
  }

  private void cleanFixture() {
    String members = "SELECT id FROM members WHERE email LIKE 'perf-ph10-002-%@synthetic.invalid'";
    jdbc.update(
        "DELETE movement FROM inventory_movements movement JOIN payments payment ON"
            + " payment.id=movement.payment_id JOIN orders common_order ON"
            + " common_order.id=payment.order_id WHERE common_order.member_id IN ("
            + members
            + ")");
    jdbc.update(
        "DELETE payment FROM payments payment JOIN orders common_order ON"
            + " common_order.id=payment.order_id WHERE common_order.member_id IN ("
            + members
            + ")");
    jdbc.update(
        "DELETE context FROM subscription_order_context context JOIN orders common_order ON"
            + " common_order.id=context.order_id WHERE common_order.member_id IN ("
            + members
            + ")");
    jdbc.update(
        "DELETE item FROM order_items item JOIN orders common_order ON"
            + " common_order.id=item.order_id WHERE common_order.member_id IN ("
            + members
            + ")");
    jdbc.update("DELETE FROM orders WHERE member_id IN (" + members + ")");
    jdbc.update("DELETE FROM billing_payment_methods WHERE member_id IN (" + members + ")");
    jdbc.update(
        "DELETE item FROM subscription_order_items item JOIN subscription_orders result ON"
            + " result.id=item.order_id JOIN subscriptions subscription ON"
            + " subscription.id=result.subscription_id WHERE subscription.member_id IN ("
            + members
            + ")");
    jdbc.update(
        "DELETE result FROM subscription_orders result JOIN subscriptions subscription ON"
            + " subscription.id=result.subscription_id WHERE subscription.member_id IN ("
            + members
            + ")");
    jdbc.update(
        "DELETE shipping FROM subscription_shipping_snapshots shipping JOIN subscriptions"
            + " subscription ON subscription.id=shipping.subscription_id WHERE"
            + " subscription.member_id IN ("
            + members
            + ")");
    jdbc.update(
        "DELETE schedule FROM subscription_schedules schedule JOIN subscriptions subscription ON"
            + " subscription.id=schedule.subscription_id WHERE subscription.member_id IN ("
            + members
            + ")");
    jdbc.update(
        "DELETE item FROM subscription_snapshot_items item JOIN subscription_snapshots snapshot ON"
            + " snapshot.id=item.snapshot_id JOIN subscriptions subscription ON"
            + " subscription.id=snapshot.subscription_id WHERE subscription.member_id IN ("
            + members
            + ")");
    jdbc.update(
        "UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id IN (" + members + ")");
    jdbc.update(
        "DELETE snapshot FROM subscription_snapshots snapshot JOIN subscriptions subscription ON"
            + " subscription.id=snapshot.subscription_id WHERE subscription.member_id IN ("
            + members
            + ")");
    jdbc.update("DELETE FROM subscriptions WHERE member_id IN (" + members + ")");
    jdbc.update("DELETE FROM pets WHERE member_id IN (" + members + ")");
    jdbc.update(
        "UPDATE members SET default_address_id=NULL WHERE email LIKE"
            + " 'perf-ph10-002-%@synthetic.invalid'");
    jdbc.update("DELETE FROM member_addresses WHERE member_id IN (" + members + ")");
    jdbc.update("DELETE FROM members WHERE email LIKE 'perf-ph10-002-%@synthetic.invalid'");
    jdbc.update(
        "UPDATE subscription_plans SET current_plan_version_id=NULL WHERE name='PERF-PH10-002"
            + " synthetic plan'");
    jdbc.update(
        "DELETE cycle FROM plan_version_delivery_cycles cycle JOIN plan_versions version ON"
            + " version.id=cycle.plan_version_id JOIN subscription_plans plan ON"
            + " plan.id=version.plan_id WHERE plan.name='PERF-PH10-002 synthetic plan'");
    jdbc.update(
        "DELETE item FROM plan_items item JOIN plan_versions version ON"
            + " version.id=item.plan_version_id JOIN subscription_plans plan ON"
            + " plan.id=version.plan_id WHERE plan.name='PERF-PH10-002 synthetic plan'");
    jdbc.update(
        "DELETE version FROM plan_versions version JOIN subscription_plans plan ON"
            + " plan.id=version.plan_id WHERE plan.name='PERF-PH10-002 synthetic plan'");
    jdbc.update("DELETE FROM subscription_plans WHERE name='PERF-PH10-002 synthetic plan'");
    jdbc.update(
        "DELETE inventory FROM inventories inventory JOIN skus sku ON sku.id=inventory.sku_id WHERE"
            + " sku.sku_code='PERF-PH10-002-SKU'");
    jdbc.update("DELETE FROM skus WHERE sku_code='PERF-PH10-002-SKU'");
    jdbc.update("DELETE FROM products WHERE name='PERF-PH10-002 synthetic product'");
    jdbc.update("DELETE FROM categories WHERE slug='perf-ph10-002-synthetic'");
  }
}
