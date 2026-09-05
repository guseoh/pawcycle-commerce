package com.pawcycle.backend.catalog.engagement.persistence;

import com.pawcycle.backend.catalog.engagement.domain.ProductReviewSummaryEntity;
import com.pawcycle.backend.catalog.engagement.domain.ReviewEntity;
import com.pawcycle.backend.catalog.brand.persistence.BrandRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
    return summaries.findById(productId).map(summary -> new CachedSummary(summary.getSourceFingerprint(), summary.getSummary()));
  }

  @Transactional
  public void saveSummary(long productId, String fingerprint, String summary, Timestamp generatedAt) {
    ProductReviewSummaryEntity entity =
        summaries
            .findById(productId)
            .orElseGet(() -> new ProductReviewSummaryEntity(productId, fingerprint, summary, generatedAt.toLocalDateTime()));
    entity.update(fingerprint, summary, generatedAt.toLocalDateTime());
    summaries.saveAndFlush(entity);
  }

  @Transactional(readOnly = true)
  public boolean hasActiveBrand(long productId) {
    return brands.hasActiveBrandForProduct(productId);
  }

  private ReviewRow toRow(ReviewEntity review) {
    return new ReviewRow(
        review.getId(), review.getRating(), review.getContent(), Timestamp.valueOf(review.getUpdatedAt()));
  }

  public record ReviewRow(long id, int rating, String content, Timestamp updatedAt) {}

  public record CachedSummary(String sourceFingerprint, String summary) {}
}
