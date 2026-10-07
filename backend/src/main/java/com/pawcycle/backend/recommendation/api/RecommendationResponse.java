package com.pawcycle.backend.recommendation.api;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse(String requestId, List<RecommendationItem> products) {
  public RecommendationResponse(List<RecommendationItem> products) {
    this(UUID.randomUUID().toString(), products);
  }

  public RecommendationResponse {
    products = List.copyOf(products);
  }
}
