package com.pawcycle.backend.recommendation;

import java.util.List;
import java.util.UUID;

record RecommendationResponse(String requestId, List<RecommendationItem> products) {
  RecommendationResponse(List<RecommendationItem> products) {
    this(UUID.randomUUID().toString(), products);
  }

  RecommendationResponse {
    products = List.copyOf(products);
  }
}
