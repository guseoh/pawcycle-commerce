package com.pawcycle.backend.recommendation.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;

public final class RecommendationMetricsFixture {
  private RecommendationMetricsFixture() {}

  public static RecommendationMetrics create(MeterRegistry registry) {
    return new RecommendationMetrics(registry);
  }
}
