package com.pawcycle.backend.subscription.automation;

import com.pawcycle.backend.common.scheduling.ScheduledJob;
import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import com.pawcycle.backend.subscription.persistence.SubscriptionMetricsQueryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Subscription delivery backlog: rounds whose date has passed without an order, and rounds on hold
 * by reason. Gauges read a refreshed snapshot and are NaN before the first successful refresh.
 */
@Component
public class SubscriptionBacklogMetrics {
  static final List<String> HOLD_REASONS = List.of(
      "MISSING_SHIPPING_ADDRESS",
      "MISSING_BILLING_METHOD",
      "PAYMENT_RETRY_EXHAUSTED",
      "PAYMENT_RETRY_STOCK_UNAVAILABLE",
      "ORDER_STOCK_UNAVAILABLE");
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final SubscriptionMetricsQueryRepository queries;
  private final ScheduledJobRunner jobs;
  private final Clock clock;
  private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

  public SubscriptionBacklogMetrics(
      MeterRegistry registry,
      SubscriptionMetricsQueryRepository queries,
      ScheduledJobRunner jobs,
      Clock clock) {
    this.queries = queries;
    this.jobs = jobs;
    this.clock = clock;
    Gauge.builder("pawcycle.subscription.schedules.overdue", snapshot,
            ref -> ref.get() == null ? Double.NaN : ref.get().overdue())
        .register(registry);
    for (String reason : HOLD_REASONS) {
      Gauge.builder("pawcycle.subscription.schedules.held", snapshot,
              ref -> ref.get() == null ? Double.NaN : ref.get().held().getOrDefault(reason, 0L))
          .tag("reason", reason.toLowerCase(Locale.ROOT))
          .register(registry);
    }
  }

  @Scheduled(
      fixedDelayString = "${pawcycle.subscription.backlog.metrics-refresh-ms:60000}",
      initialDelayString = "${pawcycle.metrics.refresh.initial-delay-ms:0}")
  public void refresh() {
    jobs.run(ScheduledJob.SUBSCRIPTION_BACKLOG_METRICS_REFRESH, this::refreshOnce);
  }

  private void refreshOnce() {
    // Same calendar as order automation: a round is due on its Seoul date.
    LocalDate today = LocalDate.now(clock.withZone(SEOUL));
    snapshot.set(new Snapshot(queries.countOverdueSchedules(today), queries.countHeldSchedulesByReason()));
  }

  record Snapshot(long overdue, Map<String, Long> held) {}
}
