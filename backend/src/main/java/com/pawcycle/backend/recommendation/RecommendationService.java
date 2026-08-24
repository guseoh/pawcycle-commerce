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

	RecommendationResponse recommend(long memberId, long petId) {
		String petType = repository.findOwnedPetType(memberId, petId);
		if (petType == null) throw new RecommendationPetNotFoundException();
		List<String> preferences = preferences(memberId, petId);
		List<RecommendationCandidate> initialCandidates = sortedCandidates(petType, preferences);
		int desired = Math.min(RESULT_LIMIT, initialCandidates.size());
		if (desired == 0) return new RecommendationResponse(List.of());

		List<RecommendationAiClient.AiRecommendation> aiRecommendations;
		boolean providerFallback = false;
		try {
			aiRecommendations = aiClient.recommend(List.copyOf(initialCandidates.subList(0, desired)), preferences);
		} catch (RuntimeException ignored) {
			aiRecommendations = List.of();
			providerFallback = true;
		}

		// 외부 AI 호출 뒤 최신 공개/SKU/재고 상태를 다시 읽어 응답 후보를 재검증한다.
		List<RecommendationCandidate> latestCandidates = sortedCandidates(petType, preferences);
		int latestDesired = Math.min(RESULT_LIMIT, latestCandidates.size());
		if (latestDesired == 0) {
			metrics.fallback();
			return new RecommendationResponse(List.of());
		}

		return new RecommendationResponse(mergeAiAndFallback(
				aiRecommendations,
				latestCandidates,
				preferences,
				latestDesired,
				providerFallback));
	}

	private List<String> preferences(long memberId, long petId) {
		List<String> all = new ArrayList<>();
		all.addAll(repository.subscriptionCategorySlugs(memberId, petId));
		all.addAll(repository.purchaseCategorySlugs(memberId));
		all.addAll(repository.wishlistCategorySlugs(memberId));
		return all.stream().distinct().toList();
	}

	private List<RecommendationCandidate> sortedCandidates(String petType, List<String> preferences) {
		return repository.findPurchasableCandidates(petType).stream()
				.filter(this::isNonMedical)
				.sorted(Comparator.comparingInt((RecommendationCandidate candidate) -> preferenceRank(candidate, preferences))
						.thenComparingLong(RecommendationCandidate::productId))
				.toList();
	}

	private List<RecommendationItem> mergeAiAndFallback(
			List<RecommendationAiClient.AiRecommendation> aiRecommendations,
			List<RecommendationCandidate> general,
			List<String> preferences,
			int desired,
			boolean providerFallback) {
		Map<Long, RecommendationCandidate> eligible = new HashMap<>();
		general.forEach(candidate -> eligible.put(candidate.productId(), candidate));
		List<RecommendationItem> result = new ArrayList<>();
		Set<Long> selected = new HashSet<>();
		boolean fallbackUsed = providerFallback;

		for (RecommendationAiClient.AiRecommendation ai : aiRecommendations) {
			if (ai == null || ai.productId() == null || !selected.add(ai.productId())) {
				fallbackUsed = true;
				continue;
			}
			RecommendationCandidate candidate = eligible.get(ai.productId());
			if (candidate == null) {
				fallbackUsed = true;
				continue;
			}
			ReasonSelection reason = safeReason(ai.reason(), candidate, preferences);
			fallbackUsed |= reason.fallback();
			result.add(toItem(candidate, reason.text()));
			if (result.size() == desired) break;
		}

		for (RecommendationCandidate candidate : general) {
			if (result.size() == desired) break;
			if (selected.add(candidate.productId())) {
				fallbackUsed = true;
				result.add(toItem(candidate, fallbackReason(candidate, preferences)));
			}
		}

		if (!fallbackUsed && result.size() == desired) metrics.success();
		else metrics.fallback();
		return List.copyOf(result);
	}

	private int preferenceRank(RecommendationCandidate candidate, List<String> preferences) {
		if (candidate.category() == null) return Integer.MAX_VALUE;
		int rank = preferences.indexOf(candidate.category().slug());
		return rank < 0 ? Integer.MAX_VALUE : rank;
	}

	private boolean isNonMedical(RecommendationCandidate candidate) {
		return containsNoMedicalTerm(candidate.name())
				&& containsNoMedicalTerm(candidate.shortDescription())
				&& (candidate.category() == null
						|| (containsNoMedicalTerm(candidate.category().name()) && containsNoMedicalTerm(candidate.category().slug())));
	}

	private boolean containsNoMedicalTerm(String value) {
		if (value == null) return true;
		String normalized = value.toLowerCase(Locale.ROOT);
		return MEDICAL_TERMS.stream().noneMatch(normalized::contains);
	}

	private ReasonSelection safeReason(String reason, RecommendationCandidate candidate, List<String> preferences) {
		if (reason == null || reason.isBlank() || reason.length() > 100 || !containsNoMedicalTerm(reason)
				|| reason.chars().noneMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HANGUL)) {
			return new ReasonSelection(fallbackReason(candidate, preferences), true);
		}
		return new ReasonSelection(reason.trim(), false);
	}

	private String fallbackReason(RecommendationCandidate candidate, List<String> preferences) {
		return candidate.category() != null && preferences.contains(candidate.category().slug())
				? "현재 관심 카테고리와 잘 맞는 상품입니다."
				: "반려동물 유형에 맞는 구매 가능 상품입니다.";
	}

	private RecommendationItem toItem(RecommendationCandidate candidate, String reason) {
		RecommendationItem.Category category = candidate.category() == null
				? null
				: new RecommendationItem.Category(candidate.category().categoryId(), candidate.category().name(), candidate.category().slug());
		return new RecommendationItem(
				candidate.productId(), candidate.name(), candidate.shortDescription(), candidate.thumbnailUrl(), category, reason);
	}

	private record ReasonSelection(String text, boolean fallback) {}
	record RecommendationResponse(List<RecommendationItem> products) { RecommendationResponse { products = List.copyOf(products); } }
	record RecommendationItem(long productId, String name, String shortDescription, String thumbnailUrl, Category category, String reason) {
		record Category(long categoryId, String name, String slug) {}
	}
}
