package com.pawcycle.backend.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RecommendationService {
	private static final int RESULT_LIMIT = 10;
	private static final List<String> MEDICAL_TERMS = List.of("의료", "질병", "치료", "약품", "처방", "병원", "medical", "disease", "treatment", "medicine", "drug", "prescription");
	private final RecommendationRepository repository;
	private final RecommendationAiClient aiClient;
	private final RecommendationMetrics metrics;

	RecommendationService(RecommendationRepository repository, RecommendationAiClient aiClient, RecommendationMetrics metrics) {
		this.repository = repository;
		this.aiClient = aiClient;
		this.metrics = metrics;
	}

	@Transactional(readOnly = true)
	RecommendationResponse recommend(long memberId, long petId) {
		String petType = repository.findOwnedPetType(memberId, petId);
		if (petType == null) throw new RecommendationPetNotFoundException();
		List<String> preferences = preferences(memberId, petId);
		List<RecommendationCandidate> general = repository.findPurchasableCandidates(petType).stream()
				.filter(this::isNonMedical)
				.sorted(Comparator.comparingInt((RecommendationCandidate candidate) -> preferenceRank(candidate, preferences))
						.thenComparingLong(RecommendationCandidate::productId))
				.toList();
		int desired = Math.min(RESULT_LIMIT, general.size());
		if (desired == 0) return new RecommendationResponse(List.of());

		List<RecommendationItem> recommended = fromAiOrFallback(general, preferences, desired);
		return new RecommendationResponse(recommended);
	}

	private List<String> preferences(long memberId, long petId) {
		List<String> all = new ArrayList<>();
		all.addAll(repository.subscriptionCategorySlugs(memberId, petId));
		all.addAll(repository.purchaseCategorySlugs(memberId));
		all.addAll(repository.wishlistCategorySlugs(memberId));
		return all.stream().distinct().toList();
	}

	private List<RecommendationItem> fromAiOrFallback(List<RecommendationCandidate> general, List<String> preferences, int desired) {
		Map<Long, RecommendationCandidate> eligible = new HashMap<>();
		general.forEach(candidate -> eligible.put(candidate.productId(), candidate));
		List<RecommendationItem> result = new ArrayList<>();
		Set<Long> selected = new HashSet<>();
		boolean complete = false;
		try {
			for (RecommendationAiClient.AiRecommendation ai : aiClient.recommend(general, preferences)) {
				if (ai == null || ai.productId() == null || !selected.add(ai.productId())) continue;
				RecommendationCandidate candidate = eligible.get(ai.productId());
				if (candidate == null) continue;
				result.add(toItem(candidate, safeReason(ai.reason(), candidate, preferences)));
				if (result.size() == desired) break;
			}
			complete = result.size() == desired;
		} catch (RuntimeException ignored) {
			// The recommendation endpoint remains available through its deterministic fallback.
		}
		for (RecommendationCandidate candidate : general) {
			if (result.size() == desired) break;
			if (selected.add(candidate.productId())) result.add(toItem(candidate, fallbackReason(candidate, preferences)));
		}
		if (complete) metrics.success(); else metrics.fallback();
		return List.copyOf(result);
	}

	private int preferenceRank(RecommendationCandidate candidate, List<String> preferences) {
		int rank = preferences.indexOf(candidate.category().slug());
		return rank < 0 ? Integer.MAX_VALUE : rank;
	}

	private boolean isNonMedical(RecommendationCandidate candidate) {
		return containsNoMedicalTerm(candidate.name()) && containsNoMedicalTerm(candidate.shortDescription())
				&& containsNoMedicalTerm(candidate.category().name()) && containsNoMedicalTerm(candidate.category().slug());
	}

	private boolean containsNoMedicalTerm(String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		return MEDICAL_TERMS.stream().noneMatch(normalized::contains);
	}

	private String safeReason(String reason, RecommendationCandidate candidate, List<String> preferences) {
		if (reason == null || reason.isBlank() || reason.length() > 100 || !containsNoMedicalTerm(reason)
				|| reason.chars().noneMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HANGUL)) {
			return fallbackReason(candidate, preferences);
		}
		return reason.trim();
	}

	private String fallbackReason(RecommendationCandidate candidate, List<String> preferences) {
		return preferences.contains(candidate.category().slug())
				? "현재 관심 카테고리와 잘 맞는 상품입니다."
				: "반려동물 유형에 맞는 구매 가능 상품입니다.";
	}

	private RecommendationItem toItem(RecommendationCandidate candidate, String reason) {
		return new RecommendationItem(candidate.productId(), candidate.name(), candidate.shortDescription(), candidate.thumbnailUrl(),
				new RecommendationItem.Category(candidate.category().categoryId(), candidate.category().name(), candidate.category().slug()), reason);
	}

	record RecommendationResponse(List<RecommendationItem> products) { RecommendationResponse { products = List.copyOf(products); } }
	record RecommendationItem(long productId, String name, String shortDescription, String thumbnailUrl, Category category, String reason) {
		record Category(long categoryId, String name, String slug) {}
	}
}
