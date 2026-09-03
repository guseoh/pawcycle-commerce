package com.pawcycle.backend.recommendation;

import java.util.List;

record RecommendationAiResponse(List<RecommendationAiClient.AiRecommendation> recommendations) {}
