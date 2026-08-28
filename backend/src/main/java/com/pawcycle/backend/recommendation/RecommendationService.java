package com.pawcycle.backend.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
		Set<Long> excluded = safeSet(repository.activeSubscriptionProductIds(memberId, petId));
		MemberSignalsView signals = new MemberSignalsView(repository.memberSignals(memberId, petId));
		List<RecommendationCandidate> initialCandidates = sortedCandidates(petType, preferences, excluded, signals);
		if (initialCandidates.isEmpty()) return new RecommendationResponse(UUID.randomUUID().toString(), List.of());
		boolean exploration = initialCandidates.size() > RESULT_LIMIT;
		int desired = exploration ? RESULT_LIMIT - 1 : Math.min(RESULT_LIMIT, initialCandidates.size());
		int aiInputSize = exploration ? Math.min(9, initialCandidates.size()) : Math.min(10, initialCandidates.size());
		List<RecommendationAiClient.AiRecommendation> aiRecommendations;
		boolean providerFallback = false;
		try {
			aiRecommendations = aiClient.recommend(List.copyOf(initialCandidates.subList(0, aiInputSize)), preferences);
		} catch (RuntimeException ignored) {
			aiRecommendations = List.of();
			providerFallback = true;
		}

		// Re-read every Catalog boundary after the optional provider call.
		List<RecommendationCandidate> latestCandidates = sortedCandidates(petType, preferences, excluded, signals);
		if (latestCandidates.isEmpty()) {
			metrics.fallback();
			return new RecommendationResponse(UUID.randomUUID().toString(), List.of());
		}
		List<RecommendationCandidate> personalized = latestCandidates.subList(0, Math.min(desired, latestCandidates.size()));
		List<RecommendationItem> result = mergeAiAndFallback(aiRecommendations, personalized, latestCandidates, preferences, desired, providerFallback, "PERSONALIZED");
		if (exploration && latestCandidates.size() > result.size()) {
			RecommendationCandidate explorationCandidate = explorationCandidate(memberId, petId, latestCandidates, result);
			if (explorationCandidate != null) result = append(result, toItem(explorationCandidate, fallbackReason(explorationCandidate, preferences), "EXPLORATION"));
		}
		return new RecommendationResponse(UUID.randomUUID().toString(), result);
	}

	private List<RecommendationCandidate> sortedCandidates(String petType, List<String> preferences, Set<Long> excluded, MemberSignalsView signals) {
		List<RecommendationCandidate> candidates = repository.findPurchasableCandidates(petType);
		if (candidates == null) candidates = List.of();
		return candidates.stream().filter(candidate -> !excluded.contains(candidate.productId())).filter(this::isNonMedical)
				.sorted(Comparator.comparingLong((RecommendationCandidate candidate) -> score(candidate, signals)).reversed()
					.thenComparing(Comparator.comparingLong(RecommendationCandidate::popularScore).reversed())
					.thenComparingLong(RecommendationCandidate::productId)).toList();
	}

	private long score(RecommendationCandidate candidate, MemberSignalsView signals) {
		long score = 0;
		String category = candidate.category() == null ? null : candidate.category().slug();
		String brand = candidate.brand() == null ? null : candidate.brand().slug();
		score += Math.max(0, signals.value().purchases().getOrDefault(candidate.productId(), 0)) * RecommendationPolicy.PURCHASE_PRODUCT;
		score += Math.max(0, signals.value().wishlists().getOrDefault(candidate.productId(), 0)) * RecommendationPolicy.WISHLIST_PRODUCT;
		score += RecommendationPolicy.cap(signals.value().clicks().getOrDefault(candidate.productId(), 0)) * RecommendationPolicy.CLICK_PRODUCT;
		score += RecommendationPolicy.cap(signals.value().views().getOrDefault(candidate.productId(), 0)) * RecommendationPolicy.VIEW_PRODUCT;
		score += contentScore(signals.value().purchaseCategories(), category, RecommendationPolicy.PURCHASE_CATEGORY);
		score += contentScore(signals.value().wishlistCategories(), category, RecommendationPolicy.WISHLIST_CATEGORY);
		score += contentScore(signals.value().subscriptionCategories(), category, RecommendationPolicy.SUBSCRIPTION_CATEGORY);
		score += contentScore(signals.value().filterCategories(), category, RecommendationPolicy.FILTER_CATEGORY, true);
		score += contentScore(signals.value().purchaseBrands(), brand, RecommendationPolicy.PURCHASE_BRAND);
		score += contentScore(signals.value().wishlistBrands(), brand, RecommendationPolicy.WISHLIST_BRAND);
		score += contentScore(signals.value().subscriptionBrands(), brand, RecommendationPolicy.SUBSCRIPTION_BRAND);
		score += contentScore(signals.value().filterBrands(), brand, RecommendationPolicy.FILTER_BRAND, true);
		for (String facet : candidate.facets()) {
			score += contentScore(signals.value().purchaseFacets(), facet, RecommendationPolicy.PURCHASE_FACET);
			score += contentScore(signals.value().wishlistFacets(), facet, RecommendationPolicy.WISHLIST_FACET);
			score += contentScore(signals.value().subscriptionFacets(), facet, RecommendationPolicy.SUBSCRIPTION_FACET);
			score += contentScore(signals.value().filterFacets(), facet, RecommendationPolicy.FILTER_FACET, true);
		}
		return score;
	}

	private long contentScore(Map<String, Integer> values, String key, int weight) { return contentScore(values, key, weight, false); }
	private long contentScore(Map<String, Integer> values, String key, int weight, boolean capped) { if (key == null) return 0; int occurrences = Math.max(0, values.getOrDefault(key, 0)); return (long) (capped ? RecommendationPolicy.cap(occurrences) : occurrences) * weight; }

	private List<RecommendationItem> mergeAiAndFallback(List<RecommendationAiClient.AiRecommendation> aiRecommendations, List<RecommendationCandidate> personalized, List<RecommendationCandidate> general, List<String> preferences, int desired, boolean providerFallback, String strategy) {
		Map<Long, RecommendationCandidate> eligible = new HashMap<>();
		personalized.forEach(candidate -> eligible.put(candidate.productId(), candidate));
		List<RecommendationItem> result = new ArrayList<>();
		Set<Long> selected = new HashSet<>();
		boolean fallbackUsed = providerFallback;
		for (RecommendationAiClient.AiRecommendation ai : aiRecommendations == null ? List.<RecommendationAiClient.AiRecommendation>of() : aiRecommendations) {
			if (ai == null || ai.productId() == null || !selected.add(ai.productId())) { fallbackUsed = true; continue; }
			RecommendationCandidate candidate = eligible.get(ai.productId());
			if (candidate == null) { fallbackUsed = true; continue; }
			ReasonSelection reason = safeReason(ai.reason(), candidate, preferences);
			fallbackUsed |= reason.fallback();
			result.add(toItem(candidate, reason.text(), strategy));
			if (result.size() == desired) break;
		}
		for (RecommendationCandidate candidate : general) {
			if (result.size() == desired) break;
			if (selected.add(candidate.productId())) { fallbackUsed = true; result.add(toItem(candidate, fallbackReason(candidate, preferences), strategy)); }
		}
		if (!fallbackUsed && result.size() == desired) metrics.success(); else metrics.fallback();
		return List.copyOf(result);
	}

	private RecommendationCandidate explorationCandidate(long memberId, long petId, List<RecommendationCandidate> candidates, List<RecommendationItem> selected) {
		Set<Long> ids = selected.stream().map(RecommendationItem::productId).collect(java.util.stream.Collectors.toSet());
		List<RecommendationCandidate> choices = candidates.stream().filter(candidate -> !ids.contains(candidate.productId())).toList();
		if (choices.isEmpty()) return null;
		Set<Long> exposed = safeSet(repository.exposedProductIds(memberId, 7));
		List<RecommendationCandidate> fresh = choices.stream().filter(candidate -> !exposed.contains(candidate.productId())).toList();
		List<RecommendationCandidate> pool = fresh.isEmpty() ? choices : fresh;
		int index = Math.floorMod((memberId + ":" + petId + ":" + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))).hashCode(), pool.size());
		return pool.get(index);
	}

	private List<RecommendationItem> append(List<RecommendationItem> items, RecommendationItem item) { List<RecommendationItem> result = new ArrayList<>(items); result.add(item); return List.copyOf(result); }
	private List<String> preferences(long memberId, long petId) { List<String> all = new ArrayList<>(); addAll(all, repository.subscriptionCategorySlugs(memberId, petId)); addAll(all, repository.purchaseCategorySlugs(memberId)); addAll(all, repository.wishlistCategorySlugs(memberId)); return all.stream().distinct().toList(); }
	private void addAll(List<String> target, List<String> values) { if (values != null) target.addAll(values); }
	private boolean isNonMedical(RecommendationCandidate candidate) { return containsNoMedicalTerm(candidate.name()) && containsNoMedicalTerm(candidate.shortDescription()) && (candidate.category() == null || (containsNoMedicalTerm(candidate.category().name()) && containsNoMedicalTerm(candidate.category().slug()))) && (candidate.brand() == null || (containsNoMedicalTerm(candidate.brand().name()) && containsNoMedicalTerm(candidate.brand().slug()))); }
	private boolean containsNoMedicalTerm(String value) { if (value == null) return true; String normalized = value.toLowerCase(Locale.ROOT); return MEDICAL_TERMS.stream().noneMatch(normalized::contains); }
	private ReasonSelection safeReason(String reason, RecommendationCandidate candidate, List<String> preferences) { if (reason == null || reason.isBlank() || reason.length() > 100 || !containsNoMedicalTerm(reason) || reason.chars().noneMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HANGUL)) return new ReasonSelection(fallbackReason(candidate, preferences), true); return new ReasonSelection(reason.trim(), false); }
	private String fallbackReason(RecommendationCandidate candidate, List<String> preferences) { return candidate.category() != null && preferences.contains(candidate.category().slug()) ? "현재 관심 카테고리와 잘 맞는 상품입니다." : "반려동물 유형에 맞는 구매 가능 상품입니다."; }
	private RecommendationItem toItem(RecommendationCandidate candidate, String reason, String strategy) { RecommendationItem.Category category = candidate.category() == null ? null : new RecommendationItem.Category(candidate.category().categoryId(), candidate.category().name(), candidate.category().slug()); return new RecommendationItem(candidate.productId(), candidate.name(), candidate.shortDescription(), candidate.thumbnailUrl(), category, reason, strategy); }
	private Set<Long> safeSet(Set<Long> values) { return values == null ? Set.of() : values; }

	private record ReasonSelection(String text, boolean fallback) {}
	record RecommendationResponse(String requestId, List<RecommendationItem> products) {
		RecommendationResponse(List<RecommendationItem> products) { this(UUID.randomUUID().toString(), products); }
		RecommendationResponse { products = List.copyOf(products); }
	}
	record RecommendationItem(long productId, String name, String shortDescription, String thumbnailUrl, Category category, String reason, String strategy) {
		RecommendationItem(long productId, String name, String shortDescription, String thumbnailUrl, Category category, String reason) { this(productId, name, shortDescription, thumbnailUrl, category, reason, "PERSONALIZED"); }
		record Category(long categoryId, String name, String slug) {}
	}

	private static final class MemberSignalsView {
		private final RecommendationRepository.MemberSignals value;
		MemberSignalsView(RecommendationRepository.MemberSignals value) { this.value = value == null ? empty() : value; }
		RecommendationRepository.MemberSignals value() { return value; }
		private static RecommendationRepository.MemberSignals empty() { return new RecommendationRepository.MemberSignals(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()); }
	}
}
