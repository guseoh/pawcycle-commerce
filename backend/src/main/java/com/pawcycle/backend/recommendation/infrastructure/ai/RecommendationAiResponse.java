package com.pawcycle.backend.recommendation.infrastructure.ai;

import com.pawcycle.backend.recommendation.application.RecommendationAiClient;

import java.util.List;

record RecommendationAiResponse(List<RecommendationAiClient.AiRecommendation> recommendations) {}
