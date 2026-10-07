package com.pawcycle.backend.recommendation.application;

import com.pawcycle.backend.recommendation.domain.RecommendationCandidate;

import java.util.List;

public interface RecommendationAiClient {
  List<AiRecommendation> recommend(
      List<RecommendationCandidate> candidates, List<String> preferredCategorySlugs);

  record AiRecommendation(Long productId, String reason) {}
}
