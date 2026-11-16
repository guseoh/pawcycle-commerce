package com.pawcycle.backend.common.scheduling;

import com.pawcycle.backend.common.logging.RedactedFailure;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Runs one execution of a scheduled job and records it the same way for every job (stage 2, O4):
 * executions by outcome, duration, processed items, and the time of the last success.
 *
 * <p>A failure is counted and logged here, once, with exception messages removed, and is not
 * rethrown: the next fixed-delay execution runs as before. Each run carries {@code jobName} and
 * {@code jobRunId} in the MDC so every log line of the run can be found together.
 */
@Component
public class ScheduledJobRunner {
  static final String EXECUTIONS = "pawcycle.job.executions";
  static final String DURATION = "pawcycle.job.duration";
  static final String ITEMS = "pawcycle.job.items";
  static final String LAST_SUCCESS = "pawcycle.job.last.success.seconds";
  static final int UNKNOWN_ITEMS = -1;
  /** Not "job": Prometheus reserves job for the scrape target and renames a clashing tag. */
  static final String JOB_TAG = "job_name";
  private static final Logger log = LoggerFactory.getLogger(ScheduledJobRunner.class);

  private final Clock clock;
  private final Map<ScheduledJob, Meters> meters = new EnumMap<>(ScheduledJob.class);

  public ScheduledJobRunner(MeterRegistry registry, Clock clock) {
    this.clock = clock;
    // Registered up front: a job that never succeeds still exposes its series.
    for (ScheduledJob job : ScheduledJob.values()) meters.put(job, new Meters(registry, job));
  }

  /** Runs a job whose result is the number of items it processed. */
  public int run(ScheduledJob job, IntSupplier work) {
    return execute(job, work);
  }

  /** Runs a job that does not report a processed-item count. */
  public void run(ScheduledJob job, Runnable work) {
    execute(job, () -> {
      work.run();
      return UNKNOWN_ITEMS;
    });
  }

  private int execute(ScheduledJob job, IntSupplier work) {
    Meters jobMeters = meters.get(job);
    String previousName = MDC.get("jobName");
    String previousRun = MDC.get("jobRunId");
    MDC.put("jobName", job.tag());
    MDC.put("jobRunId", UUID.randomUUID().toString());
    long started = System.nanoTime();
    try {
      int items = work.getAsInt();
      long elapsed = System.nanoTime() - started;
      jobMeters.success(items, elapsed, clock.millis());
      if (items > 0) {
        log.info("Scheduled job completed; job={}, items={}, durationMs={}", job.tag(), items, millis(elapsed));
      } else {
        log.debug("Scheduled job completed; job={}, items={}, durationMs={}", job.tag(), items, millis(elapsed));
      }
      return items;
    } catch (RuntimeException failure) {
      long elapsed = System.nanoTime() - started;
      jobMeters.failure(elapsed);
      log.error(
          "Scheduled job failed; job={}, durationMs={}, cause={}",
          job.tag(), millis(elapsed), failure.getClass().getSimpleName(), RedactedFailure.of(failure));
      return 0;
    } finally {
      restore("jobName", previousName);
      restore("jobRunId", previousRun);
    }
  }

  private static long millis(long nanos) {
    return TimeUnit.NANOSECONDS.toMillis(nanos);
  }

  private static void restore(String key, String previous) {
    if (previous == null) MDC.remove(key);
    else MDC.put(key, previous);
  }

  private static final class Meters {
    private final Counter successes;
    private final Counter failures;
    private final Counter items;
    private final Timer duration;
    private final AtomicLong lastSuccessMillis = new AtomicLong(-1);

    Meters(MeterRegistry registry, ScheduledJob job) {
      successes = Counter.builder(EXECUTIONS).tag(JOB_TAG, job.tag()).tag("outcome", "success").register(registry);
      failures = Counter.builder(EXECUTIONS).tag(JOB_TAG, job.tag()).tag("outcome", "failure").register(registry);
      items = Counter.builder(ITEMS).tag(JOB_TAG, job.tag()).register(registry);
      duration = Timer.builder(DURATION).tag(JOB_TAG, job.tag()).register(registry);
      Gauge.builder(LAST_SUCCESS, lastSuccessMillis,
              value -> value.get() < 0 ? Double.NaN : value.get() / 1000.0)
          .tag(JOB_TAG, job.tag())
          .register(registry);
    }

    void success(int processed, long nanos, long nowMillis) {
      successes.increment();
      if (processed > 0) items.increment(processed);
      duration.record(nanos, TimeUnit.NANOSECONDS);
      lastSuccessMillis.set(nowMillis);
    }

    void failure(long nanos) {
      failures.increment();
      duration.record(nanos, TimeUnit.NANOSECONDS);
    }
  }
}
