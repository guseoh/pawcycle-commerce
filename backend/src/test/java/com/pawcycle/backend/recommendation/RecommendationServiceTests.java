package com.pawcycle.backend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecommendationServiceTests {
  private RecommendationQueryAdapter repository;
  private RecommendationAiClient ai;
  private RecommendationService service;
  private SimpleMeterRegistry meters;

  @BeforeEach
  void setUp() {
    repository = mock(RecommendationQueryAdapter.class);
    ai = mock(RecommendationAiClient.class);
    meters = new SimpleMeterRegistry();
    service =
        new RecommendationService(repository, ai, new RecommendationMetrics(meters), Clock.systemUTC());
  }

  @Test
  void nonOwnedPetIsNotExposed() {
    when(repository.findOwnedPetType(10L, 99L)).thenReturn(null);

    assertThatThrownBy(() -> service.recommend(10L, 99L))
        .isInstanceOf(RecommendationPetNotFoundException.class);
  }

  @Test
  void invalidDuplicateAndPartialAiOutputFallsBackToValidatedGeneralCandidates() {
    when(repository.findOwnedPetType(10L, 1L)).thenReturn("DOG");
    when(repository.subscriptionCategorySlugs(10L, 1L)).thenReturn(List.of("food"));
    when(repository.purchaseCategorySlugs(10L)).thenReturn(List.of("litter"));
    when(repository.wishlistCategorySlugs(10L)).thenReturn(List.of("toy"));
    RecommendationCandidate food = candidate(1L, "사료", "food");
    RecommendationCandidate litter = candidate(2L, "모래", "litter");
    RecommendationCandidate base = candidate(3L, "장난감", "toy");
    RecommendationCandidate medical = candidate(4L, "치료 약품", "medical");
    when(repository.findPurchasableCandidates("DOG"))
        .thenReturn(List.of(base, medical, litter, food), List.of(base, medical, litter, food));
    when(ai.recommend(anyList(), anyList()))
        .thenReturn(
            List.of(
                new RecommendationAiClient.AiRecommendation(999L, "잘못된 후보입니다."),
                new RecommendationAiClient.AiRecommendation(1L, "정기배송 카테고리와 잘 맞습니다."),
                new RecommendationAiClient.AiRecommendation(1L, "중복입니다."),
                new RecommendationAiClient.AiRecommendation(2L, "English reason")));

    RecommendationResponse response = service.recommend(10L, 1L);

    assertThat(response.products())
        .extracting(RecommendationItem::productId)
        .containsExactly(1L, 2L, 3L);
    assertThat(response.products()).noneMatch(item -> item.productId() == 4L);
    assertThat(response.products().get(0).reason()).isEqualTo("정기배송 카테고리와 잘 맞습니다.");
    assertThat(response.products().get(1).reason()).isEqualTo("현재 관심 카테고리와 잘 맞는 상품입니다.");
    assertThat(
            meters
                .get("pawcycle.recommendation.ai.outcomes")
                .tag("result", "fallback")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void aiFailureReturnsGeneralRecommendationInsteadOfServerError() {
    when(repository.findOwnedPetType(10L, 1L)).thenReturn("DOG");
    when(repository.subscriptionCategorySlugs(10L, 1L)).thenReturn(List.of());
    when(repository.purchaseCategorySlugs(10L)).thenReturn(List.of());
    when(repository.wishlistCategorySlugs(10L)).thenReturn(List.of());
    RecommendationCandidate food = candidate(1L, "사료", "food");
    when(repository.findPurchasableCandidates("DOG")).thenReturn(List.of(food), List.of(food));
    when(ai.recommend(anyList(), anyList()))
        .thenThrow(new IllegalStateException("provider unavailable"));

    RecommendationResponse response = service.recommend(10L, 1L);

    assertThat(response.products())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.productId()).isEqualTo(1L);
              assertThat(item.reason()).isEqualTo("반려동물 유형에 맞는 구매 가능 상품입니다.");
            });
    assertThat(
            meters
                .get("pawcycle.recommendation.ai.outcomes")
                .tag("result", "fallback")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void aiReceivesOnlyPersonalizedTopNineWhenExplorationIsNeeded() {
    when(repository.findOwnedPetType(10L, 1L)).thenReturn("DOG");
    when(repository.subscriptionCategorySlugs(10L, 1L)).thenReturn(List.of());
    when(repository.purchaseCategorySlugs(10L)).thenReturn(List.of());
    when(repository.wishlistCategorySlugs(10L)).thenReturn(List.of());
    List<RecommendationCandidate> candidates =
        LongStream.rangeClosed(1, 11)
            .mapToObj(id -> candidate(id, "상품" + id, "category-" + id))
            .toList();
    when(repository.findPurchasableCandidates("DOG")).thenReturn(candidates, candidates);
    when(ai.recommend(anyList(), anyList())).thenReturn(List.of());

    RecommendationResponse response = service.recommend(10L, 1L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<RecommendationCandidate>> captor = ArgumentCaptor.forClass(List.class);
    verify(ai).recommend(captor.capture(), anyList());
    assertThat(captor.getValue())
        .extracting(RecommendationCandidate::productId)
        .containsExactlyElementsOf(LongStream.rangeClosed(1, 9).boxed().toList());
    assertThat(response.products()).hasSize(10);
    assertThat(response.products())
        .extracting(RecommendationItem::strategy)
        .containsExactly(
            "PERSONALIZED",
            "PERSONALIZED",
            "PERSONALIZED",
            "PERSONALIZED",
            "PERSONALIZED",
            "PERSONALIZED",
            "PERSONALIZED",
            "PERSONALIZED",
            "PERSONALIZED",
            "EXPLORATION");
    List<Long> personalizedIds =
        response.products().stream()
            .filter(item -> item.strategy().equals("PERSONALIZED"))
            .map(RecommendationItem::productId)
            .toList();
    assertThat(personalizedIds)
        .containsExactlyElementsOf(LongStream.rangeClosed(1, 9).boxed().toList());
    List<Long> explorationIds =
        response.products().stream()
            .filter(item -> item.strategy().equals("EXPLORATION"))
            .map(RecommendationItem::productId)
            .toList();
    assertThat(explorationIds).singleElement().isIn(10L, 11L);
    assertThat(personalizedIds).doesNotContainAnyElementsOf(explorationIds);
  }

  @Test
  void staleAiCandidateIsRevalidatedAfterProviderCall() {
    when(repository.findOwnedPetType(10L, 1L)).thenReturn("DOG");
    when(repository.subscriptionCategorySlugs(10L, 1L)).thenReturn(List.of());
    when(repository.purchaseCategorySlugs(10L)).thenReturn(List.of());
    when(repository.wishlistCategorySlugs(10L)).thenReturn(List.of());
    RecommendationCandidate stale = candidate(1L, "품절 예정", "food");
    RecommendationCandidate current = candidate(2L, "현재 구매 가능", "toy");
    when(repository.findPurchasableCandidates("DOG"))
        .thenReturn(List.of(stale, current), List.of(current));
    when(ai.recommend(anyList(), anyList()))
        .thenReturn(List.of(new RecommendationAiClient.AiRecommendation(1L, "이 상품을 추천합니다.")));

    RecommendationResponse response = service.recommend(10L, 1L);

    assertThat(response.products())
        .extracting(RecommendationItem::productId)
        .containsExactly(2L);
    assertThat(
            meters
                .get("pawcycle.recommendation.ai.outcomes")
                .tag("result", "fallback")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void rejectedAiReasonCountsAsFallbackEvenWhenAiSelectedAllProducts() {
    when(repository.findOwnedPetType(10L, 1L)).thenReturn("DOG");
    when(repository.subscriptionCategorySlugs(10L, 1L)).thenReturn(List.of());
    when(repository.purchaseCategorySlugs(10L)).thenReturn(List.of());
    when(repository.wishlistCategorySlugs(10L)).thenReturn(List.of());
    RecommendationCandidate food = candidate(1L, "사료", "food");
    when(repository.findPurchasableCandidates("DOG")).thenReturn(List.of(food), List.of(food));
    when(ai.recommend(anyList(), anyList()))
        .thenReturn(List.of(new RecommendationAiClient.AiRecommendation(1L, "English only")));

    RecommendationResponse response = service.recommend(10L, 1L);

    assertThat(response.products())
        .singleElement()
        .extracting(RecommendationItem::reason)
        .isEqualTo("반려동물 유형에 맞는 구매 가능 상품입니다.");
    assertThat(
            meters
                .get("pawcycle.recommendation.ai.outcomes")
                .tag("result", "fallback")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void fullyValidAiResultCountsAsSuccess() {
    when(repository.findOwnedPetType(10L, 1L)).thenReturn("DOG");
    when(repository.subscriptionCategorySlugs(10L, 1L)).thenReturn(List.of());
    when(repository.purchaseCategorySlugs(10L)).thenReturn(List.of());
    when(repository.wishlistCategorySlugs(10L)).thenReturn(List.of());
    RecommendationCandidate food = candidate(1L, "사료", "food");
    when(repository.findPurchasableCandidates("DOG")).thenReturn(List.of(food), List.of(food));
    when(ai.recommend(anyList(), anyList()))
        .thenReturn(
            List.of(new RecommendationAiClient.AiRecommendation(1L, "반려동물 유형에 잘 맞는 상품입니다.")));

    service.recommend(10L, 1L);

    assertThat(
            meters
                .get("pawcycle.recommendation.ai.outcomes")
                .tag("result", "success")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void disabledAiClientNeverCallsAModel() {
    assertThat(
            new RecommendationAiConfiguration()
                .disabledRecommendationAiClient()
                .recommend(List.of(), List.of()))
        .isEmpty();
  }

  private RecommendationCandidate candidate(long id, String name, String slug) {
    return new RecommendationCandidate(
        id,
        name,
        name + " 짧은 설명",
        null,
        "DOG",
        new RecommendationCategory(id, slug, slug));
  }
}
