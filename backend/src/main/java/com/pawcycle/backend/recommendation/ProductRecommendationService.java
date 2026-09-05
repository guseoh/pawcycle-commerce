package com.pawcycle.backend.recommendation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductRecommendationService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final RecommendationQueryAdapter repository;
  private final Clock clock;

  ProductRecommendationService(RecommendationQueryAdapter repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  RecommendationResponse popular(String petType, int limit) {
    List<RecommendationCandidate> candidates =
        repository.findPurchasableCandidates(petType).stream()
            .sorted(
                Comparator.comparingLong(RecommendationCandidate::popularScore)
                    .reversed()
                    .thenComparingLong(RecommendationCandidate::productId))
            .limit(limit)
            .toList();
    return response(candidates, "POPULAR");
  }

  @Transactional(readOnly = true)
  RecommendationResponse trending(String petType, int limit) {
    LocalDate today = LocalDate.now(clock.withZone(SEOUL));
    List<RecommendationCandidate> candidates = repository.findPurchasableCandidates(petType);
    Map<Long, RecommendationTrendScore> scores =
        repository.trendScores(
            candidates.stream().map(RecommendationCandidate::productId).toList(), today);
    List<RecommendationCandidate> trending =
        candidates.stream()
            .filter(candidate -> trendScore(scores, candidate).delta() > 0)
            .sorted(
                Comparator.comparingLong(
                        (RecommendationCandidate candidate) ->
                            trendScore(scores, candidate).delta())
                    .reversed()
                    .thenComparing(
                        Comparator.comparingLong(
                                (RecommendationCandidate candidate) ->
                                    trendScore(scores, candidate).recent())
                            .reversed())
                    .thenComparingLong(RecommendationCandidate::productId))
            .limit(limit)
            .toList();
    return response(
        trending.isEmpty()
            ? candidates.stream()
                .sorted(
                    Comparator.comparingLong(RecommendationCandidate::popularScore)
                        .reversed()
                        .thenComparingLong(RecommendationCandidate::productId))
                .limit(limit)
                .toList()
            : trending,
        "TRENDING");
  }

  @Transactional(readOnly = true)
  RecommendationResponse related(long productId, int limit) {
    RecommendationCandidate source = source(productId);
    Comparator<RecommendationCandidate> relatedOrder =
        Comparator.comparingInt(
                (RecommendationCandidate candidate) ->
                    same(candidate.category(), source.category()) ? 1 : 0)
            .reversed()
            .thenComparing(
                Comparator.comparingInt(
                        (RecommendationCandidate candidate) ->
                            same(candidate.brand(), source.brand()) ? 1 : 0)
                    .reversed())
            .thenComparing(
                Comparator.comparingInt(
                        (RecommendationCandidate candidate) ->
                            overlap(candidate.facets(), source.facets()))
                    .reversed())
            .thenComparing(
                Comparator.comparingLong(RecommendationCandidate::popularScore).reversed())
            .thenComparingLong(RecommendationCandidate::productId);
    List<RecommendationCandidate> candidates =
        repository.findPurchasableCandidates(source.petType()).stream()
            .filter(candidate -> candidate.productId() != productId)
            .sorted(relatedOrder)
            .limit(limit)
            .toList();
    return response(candidates, "RELATED");
  }

  @Transactional(readOnly = true)
  RecommendationResponse complementary(long productId, int limit) {
    RecommendationCandidate source = source(productId);
    Map<Long, Long> coPurchase = repository.coPurchaseCounts(productId);
    Comparator<RecommendationCandidate> complementaryOrder =
        Comparator.comparingLong(
                (RecommendationCandidate candidate) ->
                    coPurchase.getOrDefault(candidate.productId(), 0L))
            .reversed()
            .thenComparing(
                Comparator.comparingInt(
                        (RecommendationCandidate candidate) ->
                            differentCategory(candidate, source) ? 1 : 0)
                    .reversed())
            .thenComparing(
                Comparator.comparingLong(RecommendationCandidate::popularScore).reversed())
            .thenComparingLong(RecommendationCandidate::productId);
    List<RecommendationCandidate> candidates =
        repository.findPurchasableCandidates(source.petType()).stream()
            .filter(candidate -> candidate.productId() != productId)
            .sorted(complementaryOrder)
            .limit(limit)
            .toList();
    return response(candidates, "COMPLEMENTARY");
  }

  private RecommendationCandidate source(long productId) {
    return repository.findPurchasableCandidates(null).stream()
        .filter(candidate -> candidate.productId() == productId)
        .findFirst()
        .orElseThrow(
            () -> new RecommendationException(404, "PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
  }

  private RecommendationTrendScore trendScore(
      Map<Long, RecommendationTrendScore> scores, RecommendationCandidate candidate) {
    return scores.getOrDefault(
        candidate.productId(), new RecommendationTrendScore(0, 0));
  }

  private RecommendationResponse response(
      List<RecommendationCandidate> candidates, String strategy) {
    return new RecommendationResponse(
        java.util.UUID.randomUUID().toString(),
        candidates.stream().map(candidate -> item(candidate, strategy)).toList());
  }

  private RecommendationItem item(
      RecommendationCandidate candidate, String strategy) {
    RecommendationItemCategory category =
        candidate.category() == null
            ? null
            : new RecommendationItemCategory(
                candidate.category().categoryId(),
                candidate.category().name(),
                candidate.category().slug());
    return new RecommendationItem(
        candidate.productId(),
        candidate.name(),
        candidate.shortDescription(),
        candidate.thumbnailUrl(),
        category,
        "현재 구매 가능한 상품입니다.",
        strategy);
  }

  private boolean same(Object left, Object right) {
    return left != null && right != null && left.equals(right);
  }

  private boolean differentCategory(RecommendationCandidate left, RecommendationCandidate right) {
    return left.category() != null
        && right.category() != null
        && left.category().categoryId() != right.category().categoryId();
  }

  private int overlap(List<String> left, List<String> right) {
    return (int) left.stream().filter(new HashSet<>(right)::contains).count();
  }
}
