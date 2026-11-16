package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import com.pawcycle.backend.commerce.metrics.application.CommerceMetrics;
import com.pawcycle.backend.commerce.metrics.persistence.CommerceMetricsQueryRepository;
import com.pawcycle.backend.commerce.metrics.persistence.CommerceMetricsQueryRepository.Backlog;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommerceMetricsTests {
  private static final Instant NOW = Instant.parse("2026-09-18T13:00:00Z");
  private static final LocalDateTime NOW_UTC = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
  private final CommerceMetricsQueryRepository queries = mock(CommerceMetricsQueryRepository.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final SimpleMeterRegistry jobRegistry = new SimpleMeterRegistry();
  private CommerceMetrics metrics;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    metrics = new CommerceMetrics(registry, queries, new ScheduledJobRunner(jobRegistry, clock), clock);
    when(queries.normalProcessingExpiredBy(any())).thenReturn(new Backlog(0, null));
    when(queries.unknownPayments()).thenReturn(new Backlog(0, null));
    when(queries.billingProcessing()).thenReturn(new Backlog(0, null));
    when(queries.readyExpiredBy(any())).thenReturn(new Backlog(0, null));
    when(queries.unfinishedRefundsByStatus()).thenReturn(Map.of());
  }

  @Test
  void pendingGaugeKeepsLongValuesAboveIntegerRange() {
    when(queries.countPendingOperations()).thenReturn((long) Integer.MAX_VALUE + 100L);

    metrics.refreshPending();

    assertThat(gauge("pawcycle.commerce.operations.pending", null, null))
        .isEqualTo((double) Integer.MAX_VALUE + 100D);
  }

  @Test
  void backlogGaugesAreNotANumberBeforeTheFirstRefresh() {
    assertThat(gauge("pawcycle.payment.stuck", "kind", "normal_processing")).isNaN();
    assertThat(gauge("pawcycle.checkout.expiration.backlog", null, null)).isNaN();
    assertThat(gauge("pawcycle.refund.pending", "status", "unknown")).isNaN();
  }

  @Test
  void refreshSplitsBacklogByKindWithAgeOfTheOldestRow() {
    when(queries.normalProcessingExpiredBy(NOW_UTC.minusMinutes(10)))
        .thenReturn(new Backlog(2, NOW_UTC.minusMinutes(25)));
    when(queries.unknownPayments()).thenReturn(new Backlog(1, NOW_UTC.minusHours(1)));
    when(queries.readyExpiredBy(NOW_UTC.minusMinutes(1))).thenReturn(new Backlog(4, NOW_UTC.minusMinutes(3)));
    when(queries.unfinishedRefundsByStatus()).thenReturn(Map.of("FAILED", 3L));

    metrics.refreshPending();

    assertThat(gauge("pawcycle.payment.stuck", "kind", "normal_processing")).isEqualTo(2);
    assertThat(gauge("pawcycle.payment.stuck.oldest.age.seconds", "kind", "normal_processing"))
        .isEqualTo(25 * 60);
    assertThat(gauge("pawcycle.payment.stuck", "kind", "unknown")).isEqualTo(1);
    assertThat(gauge("pawcycle.payment.stuck.oldest.age.seconds", "kind", "unknown")).isEqualTo(3600);
    assertThat(gauge("pawcycle.payment.stuck", "kind", "billing_processing")).isZero();
    assertThat(gauge("pawcycle.payment.stuck.oldest.age.seconds", "kind", "billing_processing")).isZero();
    assertThat(gauge("pawcycle.checkout.expiration.backlog", null, null)).isEqualTo(4);
    assertThat(gauge("pawcycle.refund.pending", "status", "failed")).isEqualTo(3);
    assertThat(gauge("pawcycle.refund.pending", "status", "unknown")).isZero();
    verify(queries).normalProcessingExpiredBy(NOW_UTC.minusMinutes(10));
    verify(queries).readyExpiredBy(NOW_UTC.minusMinutes(1));
  }

  @Test
  void failedRefreshKeepsTheLastSnapshotAndIsRecordedAsAFailedRun() {
    when(queries.unknownPayments()).thenReturn(new Backlog(1, NOW_UTC.minusHours(1)));
    metrics.refreshPending();
    when(queries.unknownPayments()).thenThrow(new IllegalStateException("db down"));

    metrics.refreshPending();

    assertThat(gauge("pawcycle.payment.stuck", "kind", "unknown")).isEqualTo(1);
    assertThat(jobRegistry.get("pawcycle.job.executions")
            .tags("job", "commerce-metrics-refresh", "outcome", "failure").counter().count())
        .isEqualTo(1);
  }

  private double gauge(String name, String tag, String value) {
    var search = registry.get(name);
    if (tag != null) search = search.tag(tag, value);
    return search.gauge().value();
  }
}
