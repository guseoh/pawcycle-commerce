package com.pawcycle.backend.recommendation.persistence;

import com.pawcycle.backend.recommendation.domain.RecommendationCandidate;
import com.pawcycle.backend.recommendation.domain.RecommendationMemberSignals;
import com.pawcycle.backend.recommendation.domain.RecommendationTrendScore;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RecommendationQueryAdapter {
  private final RecommendationJpaQueryRepository portable;

  RecommendationQueryAdapter(RecommendationJpaQueryRepository portable) {
    this.portable = portable;
  }

  public Map<Long, Long> coPurchaseCounts(long productId) {
    return portable.coPurchaseCounts(productId);
  }

  public String findOwnedPetType(long memberId, long petId) {
    return portable.findOwnedPetType(memberId, petId);
  }

  public Set<Long> activeSubscriptionProductIds(long memberId, long petId) {
    return portable.activeSubscriptionProductIds(memberId, petId);
  }

  public Set<Long> exposedProductIds(long memberId, int days) {
    return portable.exposedProductIds(memberId, days);
  }

  public RecommendationMemberSignals memberSignals(long memberId, long petId) {
    return portable.memberSignals(memberId, petId);
  }

  public Map<Long, Long> popularScores(String petType) {
    return portable.popularScores(petType);
  }

  public Map<Long, RecommendationTrendScore> trendScores(List<Long> productIds, LocalDate today) {
    return portable.trendScores(productIds, today);
  }

  public List<String> subscriptionCategorySlugs(long memberId, long petId) {
    return portable.subscriptionCategorySlugs(memberId, petId);
  }

  public List<String> purchaseCategorySlugs(long memberId) {
    return portable.purchaseCategorySlugs(memberId);
  }

  public List<String> wishlistCategorySlugs(long memberId) {
    return portable.wishlistCategorySlugs(memberId);
  }

  public List<RecommendationCandidate> findPurchasableCandidates(String petType) {
    List<RecommendationCandidate> base = portable.findPurchasableCandidates(petType);
    if (base.isEmpty()) return base;
    Map<Long, Long> popular = popularScores(petType);
    return base.stream()
        .map(
            candidate ->
                new RecommendationCandidate(
                    candidate.productId(),
                    candidate.name(),
                    candidate.shortDescription(),
                    candidate.thumbnailUrl(),
                    candidate.petType(),
                    candidate.category(),
                    candidate.brand(),
                    candidate.facets(),
                    popular.getOrDefault(candidate.productId(), 0L)))
        .toList();
  }
}
