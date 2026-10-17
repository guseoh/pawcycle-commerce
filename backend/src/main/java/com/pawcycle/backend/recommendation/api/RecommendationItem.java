package com.pawcycle.backend.recommendation.api;

import com.pawcycle.backend.recommendation.model.RecommendationView.ItemView;
public record RecommendationItem(
    long productId,
    String name,
    String shortDescription,
    String thumbnailUrl,
    RecommendationItemCategory category,
    String reason,
    String strategy) {
  public static RecommendationItem from(ItemView view) {
    return new RecommendationItem(view.productId(), view.name(), view.shortDescription(),
        view.thumbnailUrl(), RecommendationItemCategory.from(view.category()), view.reason(), view.strategy());
  }

  public RecommendationItem(
      long productId,
      String name,
      String shortDescription,
      String thumbnailUrl,
      RecommendationItemCategory category,
      String reason) {
    this(productId, name, shortDescription, thumbnailUrl, category, reason, "PERSONALIZED");
  }
}
