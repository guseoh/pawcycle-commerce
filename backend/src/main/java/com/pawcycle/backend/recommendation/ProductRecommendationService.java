package com.pawcycle.backend.recommendation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductRecommendationService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final RecommendationQueryAdapter repository;
  private final NativeQueryExecutor jdbc;
  private final Clock clock;

  ProductRecommendationService(
      RecommendationQueryAdapter repository, NativeQueryExecutor jdbc, Clock clock) {
    this.repository = repository;
    this.jdbc = jdbc;
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
    Map<Long, Long> coPurchase =
        jdbc.query(
            """
            SELECT other_product.id,COUNT(DISTINCT o.id) FROM orders o JOIN payments pay ON pay.order_id=o.id AND pay.status='SUCCEEDED'
            JOIN order_items source_item ON source_item.order_id=o.id JOIN skus source_sku ON source_sku.id=source_item.sku_id
            JOIN order_items other_item ON other_item.order_id=o.id AND other_item.sku_id<>source_item.sku_id JOIN skus other_sku ON other_sku.id=other_item.sku_id
            JOIN products other_product ON other_product.id=other_sku.product_id WHERE o.source='ONE_TIME' AND o.status='PAID' AND source_sku.product_id=? AND other_product.id<>? GROUP BY other_product.id\
            """,
            rs -> {
              Map<Long, Long> values = new HashMap<>();
              while (rs.next()) values.put(rs.getLong(1), rs.getLong(2));
              return values;
            },
            productId,
            productId);
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
