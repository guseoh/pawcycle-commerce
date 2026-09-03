package com.pawcycle.backend.commerce;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Low-cardinality Commerce meters. The gauge reads a bounded cache, never the scrape thread. */
@Component
public class CommerceMetrics {
  private final MeterRegistry registry;
  private final NativeQueryExecutor jdbc;
  private final AtomicLong pending = new AtomicLong();

  public CommerceMetrics(MeterRegistry registry, NativeQueryExecutor jdbc) {
    this.registry = registry;
    this.jdbc = jdbc;
    Gauge.builder("pawcycle.commerce.operations.pending", pending, AtomicLong::get)
        .register(registry);
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

  @Scheduled(fixedDelayString = "${pawcycle.commerce.operations.refresh-ms:60000}")
  public void refreshPending() {
    Long value =
        jdbc.queryForObject(
            "SELECT (SELECT COUNT(*) FROM order_returns WHERE status='REQUESTED')+(SELECT COUNT(*)"
                + " FROM refunds WHERE status IN ('READY','FAILED','UNKNOWN'))+(SELECT COUNT(*)"
                + " FROM payments WHERE status IN ('UNKNOWN'))",
            Long.class);
    pending.set(value == null ? 0L : value);
  }
}
