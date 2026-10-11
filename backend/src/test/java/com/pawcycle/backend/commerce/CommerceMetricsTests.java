package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.metrics.application.CommerceMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.pawcycle.backend.commerce.metrics.persistence.CommerceMetricsQueryRepository;
import org.junit.jupiter.api.Test;


class CommerceMetricsTests {
  @Test
  void pendingGaugeKeepsLongValuesAboveIntegerRange() {
    CommerceMetricsQueryRepository queries = mock(CommerceMetricsQueryRepository.class);
    when(queries.countPendingOperations()).thenReturn((long) Integer.MAX_VALUE + 100L);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    CommerceMetrics metrics =
        new CommerceMetrics(registry, queries);

    metrics.refreshPending();

    assertThat(registry.get("pawcycle.commerce.operations.pending").gauge().value())
        .isEqualTo((double) Integer.MAX_VALUE + 100D);
    registry.get("pawcycle.commerce.operations.pending").gauge().value();
    org.mockito.Mockito.verify(queries).countPendingOperations();
    org.mockito.Mockito.verifyNoMoreInteractions(queries);

  }
}
