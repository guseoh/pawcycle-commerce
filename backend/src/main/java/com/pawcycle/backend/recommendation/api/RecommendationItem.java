package com.pawcycle.backend.recommendation.api;

public record RecommendationItem(
    long productId,
    String name,
    String shortDescription,
    String thumbnailUrl,
    RecommendationItemCategory category,
    String reason,
    String strategy) {
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
