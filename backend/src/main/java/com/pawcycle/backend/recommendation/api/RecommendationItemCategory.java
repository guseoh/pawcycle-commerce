package com.pawcycle.backend.recommendation.api;

import com.pawcycle.backend.recommendation.domain.RecommendationCategory;
public record RecommendationItemCategory(long categoryId, String name, String slug) {
  public static RecommendationItemCategory from(RecommendationCategory view) {
    if (view == null) return null;
    return new RecommendationItemCategory(view.categoryId(), view.name(), view.slug());
  }

}
