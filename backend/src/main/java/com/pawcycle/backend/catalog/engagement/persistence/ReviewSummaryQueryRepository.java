package com.pawcycle.backend.catalog.engagement.persistence;

import com.pawcycle.backend.catalog.brand.persistence.BrandRepository;
import com.pawcycle.backend.catalog.engagement.domain.ReviewEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReviewSummaryQueryRepository {
  private final ReviewRepository reviews;
  private final ProductReviewSummaryRepository summaries;
  private final BrandRepository brands;
  private final EntityManager entityManager;

  public ReviewSummaryQueryRepository(
      ReviewRepository reviews,
      ProductReviewSummaryRepository summaries,
      BrandRepository brands,
      EntityManager entityManager) {
    this.reviews = reviews;
    this.summaries = summaries;
    this.brands = brands;
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public SummarySnapshot snapshot(long productId) {
    long count = visibleReviewCount(productId);
    BigDecimal average = visibleAverageRating(productId);
    if (count < 3) return new SummarySnapshot(List.of(), count, average, List.of(), Optional.empty());
    return new SummarySnapshot(
        latestReviews(productId), count, average, allReviews(productId), cachedSummary(productId));
  }

  @Transactional(readOnly = true)
  public List<ReviewRow> latestReviews(long productId) {
    return reviews.findTop30ByProductIdAndVisibleTrueOrderByCreatedAtDescIdDesc(productId).stream()
        .map(this::toRow)
        .toList();
  }

  @Transactional(readOnly = true)
  public long visibleReviewCount(long productId) {
    return reviews.countByProductIdAndVisibleTrue(productId);
  }

  @Transactional(readOnly = true)
  public BigDecimal visibleAverageRating(long productId) {
    Double average = reviews.averageVisibleRating(productId);
    return average == null ? null : BigDecimal.valueOf(average);
  }

  @Transactional(readOnly = true)
  public List<ReviewRow> allReviews(long productId) {
    return reviews.findByProductIdAndVisibleTrueOrderByIdAsc(productId).stream()
        .map(this::toRow)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<CachedSummary> cachedSummary(long productId) {
    return summaries
        .findById(productId)
        .map(summary -> new CachedSummary(summary.getSourceFingerprint(), summary.getSummary()));
  }

  @Transactional
  public void saveSummary(long productId, String fingerprint, String summary, Timestamp generatedAt) {
    summaries.upsert(
        productId,
        fingerprint,
        summary,
        LocalDateTime.ofInstant(generatedAt.toInstant(), ZoneOffset.UTC));
  }

  @Transactional
  public boolean saveSummaryIfCurrent(
      long productId,
      List<ReviewRow> expectedVisibleReviews,
      String fingerprint,
      String summary,
      Timestamp generatedAt) {
    // Use a current, locking SQL read rather than managed ReviewEntity instances:
    // OpenEntityManagerInView may still hold pre-AI entity values in its first-level cache.
    // The product_id index range includes hidden reviews and protects concurrent inserts
    // through InnoDB next-key locks under the configured REPEATABLE READ isolation.
    List<Tuple> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT id, rating, content, updated_at, visible
                FROM reviews FORCE INDEX (ix_reviews_product_visible_created)
                WHERE product_id = :productId FOR UPDATE
                """,
                Tuple.class)
            .setParameter("productId", productId)
            .getResultList();
    List<ReviewRow> currentVisibleReviews =
        rows.stream()
            .filter(row -> booleanValue(row.get("visible")))
            .map(
                row ->
                    new ReviewRow(
                        ((Number) row.get("id")).longValue(),
                        ((Number) row.get("rating")).intValue(),
                        (String) row.get("content"),
                        timestampValue(row.get("updated_at"))))
            .sorted(Comparator.comparingLong(ReviewRow::id))
            .toList();
    if (!currentVisibleReviews.equals(expectedVisibleReviews)) return false;
    summaries.upsert(
        productId,
        fingerprint,
        summary,
        LocalDateTime.ofInstant(generatedAt.toInstant(), ZoneOffset.UTC));
    return true;
  }

  @Transactional(readOnly = true)
  public boolean hasActiveBrand(long productId) {
    return brands.hasActiveBrandForProduct(productId);
  }

  private ReviewRow toRow(ReviewEntity review) {
    return new ReviewRow(
        review.getId(),
        review.getRating(),
        review.getContent(),
        Timestamp.from(review.getUpdatedAt().toInstant(ZoneOffset.UTC)));
  }

  private static boolean booleanValue(Object raw) {
    return raw instanceof Boolean value ? value : ((Number) raw).intValue() != 0;
  }

  private static Timestamp timestampValue(Object raw) {
    LocalDateTime utc =
        raw instanceof LocalDateTime value
            ? value
            : raw instanceof Timestamp value
                ? value.toLocalDateTime()
                : null;
    if (utc == null) throw new IllegalStateException("Unexpected review timestamp type");
    return Timestamp.from(utc.toInstant(ZoneOffset.UTC));
  }

  public record ReviewRow(long id, int rating, String content, Timestamp updatedAt) {}

  public record CachedSummary(String sourceFingerprint, String summary) {}

  public record SummarySnapshot(
      List<ReviewRow> latestReviews,
      long count,
      BigDecimal average,
      List<ReviewRow> sourceReviews,
      Optional<CachedSummary> cached) {}
}
