package com.pawcycle.backend.subscription.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import com.pawcycle.backend.subscription.persistence.SubscriptionMetricsQueryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SubscriptionBacklogMetricsTests {
  private final SubscriptionMetricsQueryRepository queries = mock(SubscriptionMetricsQueryRepository.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void overdueUsesTheSeoulDateAndHeldIsSplitByReason() {
    // 2026-09-17T16:00Z is already 2026-09-18 in Seoul.
    Clock clock = Clock.fixed(Instant.parse("2026-09-17T16:00:00Z"), ZoneOffset.UTC);
    var metrics = new SubscriptionBacklogMetrics(
        registry, queries, new ScheduledJobRunner(new SimpleMeterRegistry(), clock), clock);
    when(queries.countOverdueSchedules(LocalDate.parse("2026-09-18"))).thenReturn(5L);
    when(queries.countHeldSchedulesByReason()).thenReturn(Map.of("MISSING_BILLING_METHOD", 2L));

    assertThat(registry.get("pawcycle.subscription.schedules.overdue").gauge().value()).isNaN();

    metrics.refresh();

    verify(queries).countOverdueSchedules(LocalDate.parse("2026-09-18"));
    assertThat(registry.get("pawcycle.subscription.schedules.overdue").gauge().value()).isEqualTo(5);
    assertThat(held("missing_billing_method")).isEqualTo(2);
    assertThat(held("order_stock_unavailable")).isZero();
    assertThat(registry.get("pawcycle.subscription.schedules.held").gauges())
        .hasSize(SubscriptionBacklogMetrics.HOLD_REASONS.size());
  }

  private double held(String reason) {
    return registry.get("pawcycle.subscription.schedules.held").tag("reason", reason).gauge().value();
  }
}
