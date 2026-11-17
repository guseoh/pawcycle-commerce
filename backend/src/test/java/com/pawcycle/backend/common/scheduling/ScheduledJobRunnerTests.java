package com.pawcycle.backend.common.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ScheduledJobRunnerTests {
  private static final Instant NOW = Instant.parse("2026-11-16T03:00:00Z");
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ScheduledJobRunner jobs =
      new ScheduledJobRunner(registry, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void everyJobExposesItsSeriesBeforeTheFirstRun() {
    for (ScheduledJob job : ScheduledJob.values()) {
      assertThat(executions(job, "success")).isZero();
      assertThat(executions(job, "failure")).isZero();
      assertThat(lastSuccess(job)).isNaN();
    }
  }

  @Test
  void successfulRunRecordsCountItemsDurationAndLastSuccess(CapturedOutput output) {
    int items = jobs.run(ScheduledJob.CHECKOUT_EXPIRATION, () -> 3);

    assertThat(items).isEqualTo(3);
    assertThat(executions(ScheduledJob.CHECKOUT_EXPIRATION, "success")).isEqualTo(1.0);
    assertThat(registry.get(ScheduledJobRunner.ITEMS).tag("job_name", "checkout-expiration").counter().count())
        .isEqualTo(3.0);
    assertThat(registry.get(ScheduledJobRunner.DURATION).tag("job_name", "checkout-expiration").timer().count())
        .isEqualTo(1);
    assertThat(lastSuccess(ScheduledJob.CHECKOUT_EXPIRATION)).isEqualTo((double) NOW.getEpochSecond());
    assertThat(output.getOut()).contains("INFO").contains("job=checkout-expiration, items=3");
  }

  @Test
  void idleRunIsNotLoggedAtInfo(CapturedOutput output) {
    jobs.run(ScheduledJob.SUBSCRIPTION_BILLING, () -> 0);
    jobs.run(ScheduledJob.COMMERCE_METRICS_REFRESH, () -> {});

    assertThat(executions(ScheduledJob.SUBSCRIPTION_BILLING, "success")).isEqualTo(1.0);
    assertThat(executions(ScheduledJob.COMMERCE_METRICS_REFRESH, "success")).isEqualTo(1.0);
    assertThat(registry.get(ScheduledJobRunner.ITEMS).tag("job_name", "commerce-metrics-refresh").counter().count())
        .isZero();
    assertThat(output.getOut()).doesNotContain("INFO");
  }

  @Test
  void failedRunIsCountedLoggedWithoutMessageAndNotRethrown(CapturedOutput output) {
    int items = jobs.run(ScheduledJob.SUBSCRIPTION_ORDER_AUTOMATION, () -> {
      throw new IllegalStateException("Duplicate entry 'owner@example.com' for key 'uk_email'");
    });

    assertThat(items).isZero();
    assertThat(executions(ScheduledJob.SUBSCRIPTION_ORDER_AUTOMATION, "failure")).isEqualTo(1.0);
    assertThat(executions(ScheduledJob.SUBSCRIPTION_ORDER_AUTOMATION, "success")).isZero();
    assertThat(lastSuccess(ScheduledJob.SUBSCRIPTION_ORDER_AUTOMATION)).isNaN();
    assertThat(output.getOut())
        .contains("ERROR")
        .contains("job=subscription-order-automation")
        .contains("cause=IllegalStateException")
        .contains("\tat ")
        .doesNotContain("owner@example.com");
  }

  @Test
  void runCarriesJobNameAndRunIdInTheMdcAndRestoresIt() {
    AtomicReference<String> name = new AtomicReference<>();
    AtomicReference<String> runId = new AtomicReference<>();
    MDC.put("jobName", "outer");

    jobs.run(ScheduledJob.SUBSCRIPTION_RECONCILIATION, () -> {
      name.set(MDC.get("jobName"));
      runId.set(MDC.get("jobRunId"));
    });

    assertThat(name).hasValue("subscription-reconciliation");
    assertThat(runId.get()).matches("[0-9a-f-]{36}");
    assertThat(MDC.get("jobName")).isEqualTo("outer");
    assertThat(MDC.get("jobRunId")).isNull();
    MDC.clear();
  }

  private double executions(ScheduledJob job, String outcome) {
    return registry.get(ScheduledJobRunner.EXECUTIONS)
        .tag("job_name", job.tag()).tag("outcome", outcome).counter().count();
  }

  private double lastSuccess(ScheduledJob job) {
    return registry.get(ScheduledJobRunner.LAST_SUCCESS).tag("job_name", job.tag()).gauge().value();
  }
}
