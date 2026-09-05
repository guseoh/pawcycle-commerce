package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.pawcycle.backend.commerce.metrics.persistence.CommerceMetricsQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CommerceMetricsTests {
  @Test
  void pendingGaugeKeepsLongValuesAboveIntegerRange() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Long.class)))
        .thenReturn((long) Integer.MAX_VALUE + 100L);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    CommerceMetrics metrics =
        new CommerceMetrics(registry, new CommerceMetricsQueryRepository(jdbc));

    metrics.refreshPending();

    assertThat(registry.get("pawcycle.commerce.operations.pending").gauge().value())
        .isEqualTo((double) Integer.MAX_VALUE + 100D);
  }
}
