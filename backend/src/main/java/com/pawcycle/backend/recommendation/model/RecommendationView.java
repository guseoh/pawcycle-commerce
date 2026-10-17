package com.pawcycle.backend.recommendation.model;

import com.pawcycle.backend.recommendation.domain.RecommendationCategory;
import java.util.List;

/** Materialized recommendation output; requestId creation remains in the use case. */
public record RecommendationView(String requestId, List<ItemView> products) {
  public RecommendationView {
    products = List.copyOf(products);
  }

  public record ItemView(
      long productId,
      String name,
      String shortDescription,
      String thumbnailUrl,
      RecommendationCategory category,
      String reason,
      String strategy) {}
}
