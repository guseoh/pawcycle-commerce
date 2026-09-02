package com.pawcycle.backend.recommendation;

import java.util.List;

interface RecommendationAiClient {
  List<AiRecommendation> recommend(
      List<RecommendationCandidate> candidates, List<String> preferredCategorySlugs);

  record AiRecommendation(Long productId, String reason) {}
}
