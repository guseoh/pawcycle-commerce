package com.pawcycle.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.recommendation.api.RecommendationResponse;
import com.pawcycle.backend.recommendation.domain.RecommendationCandidate;
import com.pawcycle.backend.recommendation.infrastructure.metrics.RecommendationMetricsFixture;
import com.pawcycle.backend.recommendation.persistence.RecommendationQueryAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecommendationBoundaryTimingTests {
  @ParameterizedTest
  @ValueSource(strings = {"empty-before-ai", "empty-after-ai", "provider-failure"})
  void requestIdIsCreatedOnceAtOriginalReturnAfterProviderAndOutcomeMetric(String path) {
    var queries = mock(RecommendationQueryAdapter.class);
    var ai = mock(RecommendationAiClient.class);
    var meters = new SimpleMeterRegistry();
    var candidate = candidate(1L);
    when(queries.findOwnedPetType(10L, 1L)).thenReturn("DOG");
    if (path.equals("empty-before-ai")) when(queries.findPurchasableCandidates("DOG")).thenReturn(List.of());
    else {
      when(queries.findPurchasableCandidates("DOG")).thenReturn(List.of(candidate),
          path.equals("empty-after-ai") ? List.of() : List.of(candidate));
      when(ai.recommend(anyList(), anyList())).thenThrow(new IllegalStateException("fake provider failure"));
    }
    var service = new RecommendationService(queries, ai, RecommendationMetricsFixture.create(meters), Clock.systemUTC());
    UUID expected = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    try (var uuids = mockStatic(UUID.class)) {
      uuids.when(UUID::randomUUID).thenAnswer(call -> {
        verify(queries, times(path.equals("empty-before-ai") ? 1 : 2)).findPurchasableCandidates("DOG");
        if (path.equals("empty-before-ai")) {
          verifyNoInteractions(ai);
          assertThat(meters.get("pawcycle.recommendation.ai.outcomes").tag("result", "fallback").counter().count()).isZero();
          assertThat(meters.get("pawcycle.recommendation.ai.outcomes").tag("result", "success").counter().count()).isZero();
        } else {
          verify(ai).recommend(anyList(), anyList());
          assertThat(meters.get("pawcycle.recommendation.ai.outcomes").tag("result", "fallback").counter().count()).isEqualTo(1);
        }
        return expected;
      });
      var result = service.recommend(10L, 1L);
      assertThat(result.requestId()).isEqualTo(expected.toString());
      assertThat(result.products()).hasSize(path.equals("provider-failure") ? 1 : 0);
      assertThat(RecommendationResponse.from(result).requestId()).isEqualTo(expected.toString());
      uuids.verify(UUID::randomUUID, times(1));
    }
  }

  @Test
  void productResponseUuidStillPrecedesItemMaterializationFailure() {
    var queries = mock(RecommendationQueryAdapter.class);
    var candidate = mock(RecommendationCandidate.class);
    when(candidate.name()).thenThrow(new IllegalStateException("materialization probe"));
    when(queries.findPurchasableCandidates("DOG")).thenReturn(List.of(candidate));
    UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    try (var uuids = mockStatic(UUID.class)) {
      uuids.when(UUID::randomUUID).thenAnswer(call -> {
        verify(queries).findPurchasableCandidates("DOG");
        return id;
      });
      assertThatThrownBy(() -> new ProductRecommendationService(queries, Clock.systemUTC()).popular("DOG", 10))
          .isInstanceOf(IllegalStateException.class).hasMessage("materialization probe");
      uuids.verify(UUID::randomUUID, times(1));
    }
  }

  @Test
  void explorationUsesSeoulDateAcrossItsExactMidnightAndFreshRequestIds() {
    var queries = mock(RecommendationQueryAdapter.class);
    var ai = mock(RecommendationAiClient.class);
    when(queries.findOwnedPetType(10L, 1L)).thenReturn("DOG");
    when(queries.findPurchasableCandidates("DOG")).thenReturn(LongStream.rangeClosed(1, 11).mapToObj(RecommendationBoundaryTimingTests::candidate).toList());
    when(ai.recommend(anyList(), anyList())).thenReturn(List.of());
    List<Instant> instants = List.of(Instant.parse("2026-10-17T14:59:59.999999Z"), Instant.parse("2026-10-17T15:00:00Z"));
    String prior = null;
    for (int i = 0; i < instants.size(); i++) {
      var service = new RecommendationService(queries, ai, RecommendationMetricsFixture.create(new SimpleMeterRegistry()), Clock.fixed(instants.get(i), ZoneOffset.UTC));
      var result = service.recommend(10L, 1L);
      LocalDate expectedDate = LocalDate.of(2026, 10, 17).plusDays(i);
      long expectedId = List.of(10L, 11L).get(Math.floorMod(("10:1:" + expectedDate).hashCode(), 2));
      assertThat(result.products().getLast().productId()).isEqualTo(expectedId);
      assertThat(result.products().getLast().strategy()).isEqualTo("EXPLORATION");
      assertThat(result.requestId()).isNotEqualTo(prior);
      prior = result.requestId();
    }
    verify(ai, times(2)).recommend(anyList(), anyList());
  }

  private static RecommendationCandidate candidate(long id) {
    return new RecommendationCandidate(id, "상품" + id, "설명", null, "DOG", null);
  }
}
