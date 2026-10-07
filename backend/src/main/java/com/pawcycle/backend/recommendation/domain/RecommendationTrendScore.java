package com.pawcycle.backend.recommendation.domain;

public record RecommendationTrendScore(long recent, long previous) {
  public long delta() {
    return recent - previous;
  }
}
