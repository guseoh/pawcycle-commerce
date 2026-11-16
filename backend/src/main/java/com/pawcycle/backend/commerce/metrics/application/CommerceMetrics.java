package com.pawcycle.backend.commerce.metrics.application;

import com.pawcycle.backend.common.scheduling.SchedulerNames;
import com.pawcycle.backend.common.scheduling.ScheduledJob;
import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import com.pawcycle.backend.commerce.metrics.persistence.CommerceMetricsQueryRepository;
import com.pawcycle.backend.commerce.metrics.persistence.CommerceMetricsQueryRepository.Backlog;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality Commerce meters. Gauges read a snapshot refreshed by a scheduled job, never the
 * database on the scrape thread. Before the first successful refresh the backlog gauges are NaN.
 */
@Component
public class CommerceMetrics {
  /** Same grace as the #417 recovery command: a PROCESSING payment this long past expiry is stuck. */
  static final Duration PROCESSING_GRACE = Duration.ofMinutes(10);
  /** The expiration job runs every minute; a READY payment this long past expiry was missed. */
  static final Duration EXPIRATION_GRACE = Duration.ofMinutes(1);
  static final List<String> STUCK_KINDS = List.of("normal_processing", "unknown", "billing_processing");
  static final List<String> REFUND_STATUSES = List.of("READY", "PROCESSING", "FAILED", "UNKNOWN");

  private final MeterRegistry registry;
  private final CommerceMetricsQueryRepository queries;
  private final ScheduledJobRunner jobs;
  private final Clock clock;
  private final AtomicLong pending = new AtomicLong();
  private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

  public CommerceMetrics(
      MeterRegistry registry,
      CommerceMetricsQueryRepository queries,
      ScheduledJobRunner jobs,
      Clock clock) {
    this.registry = registry;
    this.queries = queries;
    this.jobs = jobs;
    this.clock = clock;
    Gauge.builder("pawcycle.commerce.operations.pending", pending, AtomicLong::get)
        .register(registry);
    // Registered up front so dashboards and alerts see the series before the first event
    // (and right after a restart), instead of an empty panel.
    registry.timer("pawcycle.commerce.delivery.transition");
    registry.timer("pawcycle.commerce.refund.provider");
    for (String outcome : List.of("SUCCEEDED", "FAILED", "UNKNOWN")) {
      registry.counter("pawcycle.commerce.refund", "outcome", outcome);
    }
    for (String kind : STUCK_KINDS) {
      gauge("pawcycle.payment.stuck", "kind", kind, value -> value.stuck().get(kind).count());
      gauge("pawcycle.payment.stuck.oldest.age.seconds", "kind", kind,
          value -> value.ageSeconds(value.stuck().get(kind)));
    }
    Gauge.builder("pawcycle.checkout.expiration.backlog", snapshot,
            ref -> ref.get() == null ? Double.NaN : ref.get().expirationBacklog())
        .register(registry);
    for (String status : REFUND_STATUSES) {
      gauge("pawcycle.refund.pending", "status", status.toLowerCase(java.util.Locale.ROOT),
          value -> value.refunds().getOrDefault(status, 0L));
    }
  }

  public Timer.Sample timer() {
    return Timer.start(registry);
  }

  public void stop(Timer.Sample sample, String name) {
    sample.stop(registry.timer("pawcycle.commerce." + name));
  }

  public void count(String name, String outcome) {
    registry.counter("pawcycle.commerce." + name, "outcome", outcome).increment();
  }

  @Scheduled(
      scheduler = SchedulerNames.METRICS,
      fixedDelayString = "${pawcycle.commerce.operations.refresh-ms:60000}",
      initialDelayString = "${pawcycle.metrics.refresh.initial-delay-ms:0}")
  public void refreshPending() {
    jobs.run(ScheduledJob.COMMERCE_METRICS_REFRESH, this::refreshOnce);
  }

  private void refreshOnce() {
    Instant now = clock.instant();
    LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    Snapshot next = new Snapshot(
        now,
        Map.of(
            "normal_processing", queries.normalProcessingExpiredBy(nowUtc.minus(PROCESSING_GRACE)),
            "unknown", queries.unknownPayments(),
            "billing_processing", queries.billingProcessing()),
        queries.readyExpiredBy(nowUtc.minus(EXPIRATION_GRACE)).count(),
        queries.unfinishedRefundsByStatus());
    pending.set(queries.countPendingOperations());
    snapshot.set(next);
  }

  private void gauge(String name, String tag, String value, ToDoubleFunction<Snapshot> read) {
    Gauge.builder(name, snapshot, ref -> ref.get() == null ? Double.NaN : read.applyAsDouble(ref.get()))
        .tag(tag, value)
        .register(registry);
  }

  /**
   * One refresh result. Ages are measured from the refresh time: {@code normal_processing} from the
   * payment's expiry (no PROCESSING start time is stored), the other kinds from the request time.
   */
  record Snapshot(
      Instant refreshedAt,
      Map<String, Backlog> stuck,
      long expirationBacklog,
      Map<String, Long> refunds) {
    double ageSeconds(Backlog backlog) {
      if (backlog.oldest() == null) return 0;
      Instant oldest = backlog.oldest().toInstant(ZoneOffset.UTC);
      return Math.max(0, Duration.between(oldest, refreshedAt).toSeconds());
    }
  }
}
