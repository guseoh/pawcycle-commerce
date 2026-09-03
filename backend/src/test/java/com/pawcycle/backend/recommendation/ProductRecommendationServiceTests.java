package com.pawcycle.backend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;

class ProductRecommendationServiceTests {
  @Test
  void trendingUsesOneBatchScoreLookupAndToleratesCandidatesWithoutActivity() {
    RecommendationQueryAdapter repository = mock(RecommendationQueryAdapter.class);
    RecommendationCandidate active = candidate(1L);
    RecommendationCandidate inactive = candidate(2L);
    when(repository.findPurchasableCandidates("DOG")).thenReturn(List.of(active, inactive));
    when(repository.trendScores(anyList(), any(LocalDate.class)))
        .thenReturn(Map.of(1L, new RecommendationTrendScore(10, 0)));

    RecommendationResponse response =
        new ProductRecommendationService(repository, mock(NativeQueryExecutor.class), Clock.systemUTC())
            .trending("DOG", 10);

    assertThat(response.products())
        .extracting(RecommendationItem::productId)
        .containsExactly(1L);
    verify(repository).trendScores(eq(List.of(1L, 2L)), any(LocalDate.class));
  }

  private RecommendationCandidate candidate(long id) {
    return new RecommendationCandidate(
        id, "상품" + id, "설명", null, "DOG", new RecommendationCategory(id, "food", "사료"));
  }
}
