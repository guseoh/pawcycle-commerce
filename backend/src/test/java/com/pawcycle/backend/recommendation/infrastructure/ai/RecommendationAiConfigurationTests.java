package com.pawcycle.backend.recommendation.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationAiConfigurationTests {
  @Test
  void disabledAiClientNeverCallsAModel() {
    assertThat(
            new RecommendationAiConfiguration()
                .disabledRecommendationAiClient()
                .recommend(List.of(), List.of()))
        .isEmpty();
  }
}
