package com.pawcycle.backend.recommendation;

record RecommendationTrendScore(long recent, long previous) {
  long delta() {
    return recent - previous;
  }
}
