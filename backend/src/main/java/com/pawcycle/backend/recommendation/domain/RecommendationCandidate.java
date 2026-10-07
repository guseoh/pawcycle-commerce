package com.pawcycle.backend.recommendation.domain;

public record RecommendationCandidate(
    long productId,
    String name,
    String shortDescription,
    String thumbnailUrl,
    String petType,
    RecommendationCategory category,
    RecommendationBrand brand,
    java.util.List<String> facets,
    long popularScore) {

  public RecommendationCandidate(
      long productId,
      String name,
      String shortDescription,
      String thumbnailUrl,
      String petType,
      RecommendationCategory category) {
    this(
        productId,
        name,
        shortDescription,
        thumbnailUrl,
        petType,
        category,
        null,
        java.util.List.of(),
        0);
  }

  public RecommendationCandidate {
    facets = java.util.List.copyOf(facets == null ? java.util.List.of() : facets);
  }

}
