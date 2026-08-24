package com.pawcycle.backend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationServiceTests {
	private RecommendationRepository repository;
	private RecommendationAiClient ai;
	private RecommendationService service;
	private SimpleMeterRegistry meters;

	@BeforeEach
	void setUp() {
		repository = mock(RecommendationRepository.class);
		ai = mock(RecommendationAiClient.class);
		meters = new SimpleMeterRegistry();
		service = new RecommendationService(repository, ai, new RecommendationMetrics(meters));
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
		when(repository.findPurchasableCandidates("DOG")).thenReturn(List.of(base, medical, litter, food));
		when(ai.recommend(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(
				new RecommendationAiClient.AiRecommendation(999L, "잘못된 후보입니다."),
				new RecommendationAiClient.AiRecommendation(1L, "정기배송 카테고리와 잘 맞습니다."),
				new RecommendationAiClient.AiRecommendation(1L, "중복입니다."),
				new RecommendationAiClient.AiRecommendation(2L, "English reason")));

		RecommendationService.RecommendationResponse response = service.recommend(10L, 1L);

		assertThat(response.products()).extracting(RecommendationService.RecommendationItem::productId)
				.containsExactly(1L, 2L, 3L);
		assertThat(response.products()).noneMatch(item -> item.productId() == 4L);
		assertThat(response.products().get(0).reason()).isEqualTo("정기배송 카테고리와 잘 맞습니다.");
		assertThat(response.products().get(1).reason()).isEqualTo("현재 관심 카테고리와 잘 맞는 상품입니다.");
		assertThat(meters.get("pawcycle.recommendation.ai.outcomes").tag("result", "fallback").counter().count()).isEqualTo(1);
	}

	@Test
	void aiFailureReturnsGeneralRecommendationInsteadOfServerError() {
		when(repository.findOwnedPetType(10L, 1L)).thenReturn("DOG");
		when(repository.subscriptionCategorySlugs(10L, 1L)).thenReturn(List.of());
		when(repository.purchaseCategorySlugs(10L)).thenReturn(List.of());
		when(repository.wishlistCategorySlugs(10L)).thenReturn(List.of());
		when(repository.findPurchasableCandidates("DOG")).thenReturn(List.of(candidate(1L, "사료", "food")));
		when(ai.recommend(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
				.thenThrow(new IllegalStateException("provider unavailable"));

		RecommendationService.RecommendationResponse response = service.recommend(10L, 1L);

		assertThat(response.products()).singleElement().satisfies(item -> {
			assertThat(item.productId()).isEqualTo(1L);
			assertThat(item.reason()).isEqualTo("반려동물 유형에 맞는 구매 가능 상품입니다.");
		});
		assertThat(meters.get("pawcycle.recommendation.ai.outcomes").tag("result", "fallback").counter().count()).isEqualTo(1);
	}

	@Test
	void disabledAiClientNeverCallsAModel() {
		assertThat(new RecommendationAiConfiguration().disabledRecommendationAiClient().recommend(List.of(), List.of())).isEmpty();
	}

	private RecommendationCandidate candidate(long id, String name, String slug) {
		return new RecommendationCandidate(id, name, name + " 짧은 설명", null, "DOG",
				new RecommendationCandidate.Category(id, slug, slug));
	}
}
