package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.metrics.persistence.CommerceMetricsQueryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Low-cardinality Commerce meters. The gauge reads a bounded cache, never the scrape thread. */
@Component
public class CommerceMetrics {
  private final MeterRegistry registry;
  private final CommerceMetricsQueryRepository queries;
  private final AtomicLong pending = new AtomicLong();

  public CommerceMetrics(MeterRegistry registry, CommerceMetricsQueryRepository queries) {
    this.registry = registry;
    this.queries = queries;
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
    pending.set(queries.countPendingOperations());
  }
}
