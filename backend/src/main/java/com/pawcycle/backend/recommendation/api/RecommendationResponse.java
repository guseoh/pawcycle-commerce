package com.pawcycle.backend.recommendation.api;

import com.pawcycle.backend.recommendation.model.RecommendationView;
import java.util.List;
import java.util.UUID;

public record RecommendationResponse(String requestId, List<RecommendationItem> products) {
  public static RecommendationResponse from(RecommendationView view) {
    return new RecommendationResponse(view.requestId(),
        view.products().stream().map(RecommendationItem::from).toList());
  }

  public RecommendationResponse(List<RecommendationItem> products) {
    this(UUID.randomUUID().toString(), products);
  }

  public RecommendationResponse {
    products = List.copyOf(products);
  }
}
