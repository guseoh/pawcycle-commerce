package com.pawcycle.backend.catalog.engagement.persistence;

import com.pawcycle.backend.catalog.brand.persistence.BrandRepository;
import com.pawcycle.backend.catalog.engagement.domain.ReviewEntity;
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

  public ReviewSummaryQueryRepository(
      ReviewRepository reviews,
      ProductReviewSummaryRepository summaries,
      BrandRepository brands) {
    this.reviews = reviews;
    this.summaries = summaries;
    this.brands = brands;
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
    // Lock the entire product review range (including hidden rows) to serialize writes and
    // prevent an obsolete AI response from overwriting a summary based on newer reviews.
    List<ReviewRow> currentVisibleReviews =
        reviews.findByProductIdForUpdate(productId).stream()
            .filter(ReviewEntity::isVisible)
            .map(this::toRow)
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

  public record ReviewRow(long id, int rating, String content, Timestamp updatedAt) {}

  public record CachedSummary(String sourceFingerprint, String summary) {}

  public record SummarySnapshot(
      List<ReviewRow> latestReviews,
      long count,
      BigDecimal average,
      List<ReviewRow> sourceReviews,
      Optional<CachedSummary> cached) {}
}
