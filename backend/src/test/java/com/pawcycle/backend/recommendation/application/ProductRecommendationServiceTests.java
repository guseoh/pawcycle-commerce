package com.pawcycle.backend.recommendation.application;

import com.pawcycle.backend.recommendation.model.RecommendationView.ItemView;
import com.pawcycle.backend.recommendation.model.RecommendationView;
import com.pawcycle.backend.recommendation.domain.RecommendationCandidate;
import com.pawcycle.backend.recommendation.domain.RecommendationCategory;
import com.pawcycle.backend.recommendation.domain.RecommendationTrendScore;
import com.pawcycle.backend.recommendation.persistence.RecommendationQueryAdapter;

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

class ProductRecommendationServiceTests {
  @Test
  void trendingUsesOneBatchScoreLookupAndToleratesCandidatesWithoutActivity() {
    RecommendationQueryAdapter repository = mock(RecommendationQueryAdapter.class);
    RecommendationCandidate active = candidate(1L);
    RecommendationCandidate inactive = candidate(2L);
    when(repository.findPurchasableCandidates("DOG")).thenReturn(List.of(active, inactive));
    when(repository.trendScores(anyList(), any(LocalDate.class)))
        .thenReturn(Map.of(1L, new RecommendationTrendScore(10, 0)));

    RecommendationView response =
        new ProductRecommendationService(repository, Clock.systemUTC())
            .trending("DOG", 10);

    assertThat(response.products())
        .extracting(ItemView::productId)
        .containsExactly(1L);
    verify(repository).trendScores(eq(List.of(1L, 2L)), any(LocalDate.class));
  }

  @Test
  void trendingSortsAllPositiveDeltasByDeltaRecentThenIdAndKeepsTheLimit() {
    RecommendationQueryAdapter repository = mock(RecommendationQueryAdapter.class);
    when(repository.findPurchasableCandidates("DOG"))
        .thenReturn(List.of(candidate(5), candidate(3), candidate(1), candidate(4), candidate(2)));
    LocalDate today = LocalDate.of(2026,9,18);
    when(repository.trendScores(List.of(5L,3L,1L,4L,2L), today)).thenReturn(Map.of(
        1L, new RecommendationTrendScore(4,0),
        2L, new RecommendationTrendScore(10,6),
        3L, new RecommendationTrendScore(10,6),
        4L, new RecommendationTrendScore(1,5)));
    Clock clock = Clock.fixed(java.time.Instant.parse("2026-09-17T15:00:00Z"), java.time.ZoneOffset.UTC);
    var service = new ProductRecommendationService(repository, clock);
    assertThat(service.trending("DOG", 10).products()).extracting(ItemView::productId)
        .containsExactly(2L,3L,1L);
    assertThat(service.trending("DOG", 2).products()).extracting(ItemView::productId)
        .containsExactly(2L,3L);
    verify(repository, org.mockito.Mockito.times(2)).trendScores(List.of(5L,3L,1L,4L,2L), today);
  }

  @Test
  void trendingFallsBackToPopularityThenIdWhenEveryDeltaIsNonpositiveOrMissing() {
    RecommendationQueryAdapter repository = mock(RecommendationQueryAdapter.class);
    var first = candidate(1);
    var second = candidate(2);
    var third = candidate(3);
    when(repository.findPurchasableCandidates("DOG")).thenReturn(List.of(
        withPopularity(third, 7), withPopularity(first, 1), withPopularity(second, 7)));
    when(repository.trendScores(anyList(), any(LocalDate.class)))
        .thenReturn(Map.of(2L,new RecommendationTrendScore(4,4), 3L,new RecommendationTrendScore(1,5)))
        .thenReturn(Map.of());
    var service = new ProductRecommendationService(repository, Clock.systemUTC());
    assertThat(service.trending("DOG",2).products()).extracting(ItemView::productId)
        .containsExactly(2L,3L);
    assertThat(service.trending("DOG",10).products()).extracting(ItemView::productId)
        .containsExactly(2L,3L,1L);
  }

  private RecommendationCandidate withPopularity(RecommendationCandidate candidate, long score) {
    return new RecommendationCandidate(candidate.productId(), candidate.name(), candidate.shortDescription(),
        candidate.thumbnailUrl(), candidate.petType(), candidate.category(), candidate.brand(), candidate.facets(), score);
  }

  private RecommendationCandidate candidate(long id) {
    return new RecommendationCandidate(
        id, "상품" + id, "설명", null, "DOG", new RecommendationCategory(id, "food", "사료"));
  }
}
