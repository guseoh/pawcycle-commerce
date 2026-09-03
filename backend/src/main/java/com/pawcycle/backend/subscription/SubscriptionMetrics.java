package com.pawcycle.backend.subscription;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionMetrics {
  private static final Duration RETENTION = Duration.ofDays(30);
  private static final String CREATION_TABLE = "subscription_creation_idempotency_results";
  private static final String COMMAND_TABLE = "subscription_command_idempotency_results";

  private final MeterRegistry registry;
  private final NativeQueryExecutor jdbc;
  private final Clock clock;
  private final Counter reconciliationExecutions;
  private final Counter reconciliationProcessed;
  private final Counter reconciliationFailures;
  private final Timer reconciliationDuration;
  private final Counter cleanupSuccesses;
  private final Counter cleanupFailures;
  private final Timer cleanupDuration;
  private final Counter creationRepairs;
  private final Counter commandRepairs;
  private final Counter creationDeletes;
  private final Counter commandDeletes;
  private final AtomicReference<IdempotencyGaugeSnapshot> idempotencyGaugeSnapshot =
      new AtomicReference<>();
  private final AtomicReference<java.time.Instant> lastGaugeRefresh = new AtomicReference<>();
  private final Counter gaugeRefreshFailures;

  public SubscriptionMetrics(MeterRegistry registry, NativeQueryExecutor jdbc, Clock clock) {
    this.registry = registry;
    this.jdbc = jdbc;
    this.clock = clock;
    this.reconciliationExecutions =
        registry.counter("pawcycle.subscription.reconciliation.executions");
    this.reconciliationProcessed =
        registry.counter("pawcycle.subscription.reconciliation.processed");
    this.reconciliationFailures = registry.counter("pawcycle.subscription.reconciliation.failures");
    this.reconciliationDuration = registry.timer("pawcycle.subscription.reconciliation.duration");
    this.cleanupSuccesses =
        registry.counter(
            "pawcycle.subscription.idempotency.cleanup.executions", "result", "success");
    this.cleanupFailures =
        registry.counter(
            "pawcycle.subscription.idempotency.cleanup.executions", "result", "failure");
    this.cleanupDuration = registry.timer("pawcycle.subscription.idempotency.cleanup.duration");
    this.gaugeRefreshFailures =
        registry.counter("pawcycle.subscription.idempotency.metrics.refresh.failures");
    this.creationRepairs = cleanupRows("creation", "repair");
    this.commandRepairs = cleanupRows("command", "repair");
    this.creationDeletes = cleanupRows("creation", "delete");
    this.commandDeletes = cleanupRows("command", "delete");
    registerIdempotencyGauges("creation", CREATION_TABLE);
    registerIdempotencyGauges("command", COMMAND_TABLE);
    Gauge.builder(
            "pawcycle.subscription.idempotency.metrics.refresh.last.success",
            this,
            metrics ->
                metrics.lastGaugeRefresh.get() == null
                    ? Double.NaN
                    : metrics.lastGaugeRefresh.get().getEpochSecond())
        .register(registry);
    Gauge.builder(
            "pawcycle.subscription.idempotency.metrics.refresh.age.seconds",
            this,
            metrics ->
                metrics.lastGaugeRefresh.get() == null
                    ? Double.NaN
                    : Duration.between(metrics.lastGaugeRefresh.get(), metrics.clock.instant())
                        .toSeconds())
        .register(registry);
  }

  @Scheduled(
      scheduler = "idempotencyMetricsTaskScheduler",
      fixedDelayString = "${pawcycle.subscription.idempotency.metrics-refresh-ms:60000}")
  void refreshIdempotencyGauges() {
    try {
      idempotencyGaugeSnapshot.set(
          new IdempotencyGaugeSnapshot(
              countCompletedRows(CREATION_TABLE),
              countCleanupCandidates(CREATION_TABLE),
              countCompletedRows(COMMAND_TABLE),
              countCleanupCandidates(COMMAND_TABLE)));
      lastGaugeRefresh.set(clock.instant());
    } catch (RuntimeException exception) {
      gaugeRefreshFailures.increment();
    }
  }

  Timer.Sample startReconciliation() {
    return Timer.start(registry);
  }

  void finishReconciliation(Timer.Sample sample, int processed, int failures) {
    reconciliationExecutions.increment();
    increment(reconciliationProcessed, processed);
    increment(reconciliationFailures, failures);
    sample.stop(reconciliationDuration);
  }

  Timer.Sample startCleanup() {
    return Timer.start(registry);
  }

  void cleanupSucceeded(SubscriptionIdempotencyCleanupResult result) {
    cleanupSuccesses.increment();
    increment(creationRepairs, result.creationRepaired());
    increment(commandRepairs, result.commandRepaired());
    increment(creationDeletes, result.creationDeleted());
    increment(commandDeletes, result.commandDeleted());
  }

  void cleanupFailed() {
    cleanupFailures.increment();
  }

  void finishCleanup(Timer.Sample sample) {
    sample.stop(cleanupDuration);
  }

  private Counter cleanupRows(String scope, String operation) {
    return registry.counter(
        "pawcycle.subscription.idempotency.cleanup.rows", "scope", scope, "operation", operation);
  }

  private void registerIdempotencyGauges(String scope, String table) {
    Gauge.builder(
            "pawcycle.subscription.idempotency.retained.rows",
            this,
            metrics -> metrics.gaugeValue(scope, true))
        .tag("scope", scope)
        .register(registry);
    Gauge.builder(
            "pawcycle.subscription.idempotency.cleanup.candidates",
            this,
            metrics -> metrics.gaugeValue(scope, false))
        .tag("scope", scope)
        .register(registry);
  }

  private double countCompletedRows(String table) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE completed_at IS NOT NULL", Long.class);
  }

  private double countCleanupCandidates(String table) {
    LocalDateTime cutoff =
        LocalDateTime.ofInstant(clock.instant().minus(RETENTION), ZoneOffset.UTC);
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE completed_at < ?", Long.class, cutoff);
  }

  private static void increment(Counter counter, int amount) {
    if (amount > 0) {
      counter.increment(amount);
    }
  }

  private double gaugeValue(String scope, boolean retained) {
    IdempotencyGaugeSnapshot snapshot = idempotencyGaugeSnapshot.get();
    if (snapshot == null) return Double.NaN;
    return retained ? snapshot.retainedRows(scope) : snapshot.cleanupCandidates(scope);
  }

  private record IdempotencyGaugeSnapshot(
      double creationRetainedRows,
      double creationCleanupCandidates,
      double commandRetainedRows,
      double commandCleanupCandidates) {
    double retainedRows(String scope) {
      return scope.equals("creation") ? creationRetainedRows : commandRetainedRows;
    }

    double cleanupCandidates(String scope) {
      return scope.equals("creation") ? creationCleanupCandidates : commandCleanupCandidates;
    }
  }
}
