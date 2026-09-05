package com.pawcycle.backend.catalog.engagement.application;

import com.pawcycle.backend.catalog.product.application.ProductNotFoundException;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.engagement.persistence.ReviewSummaryQueryRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewSummaryService {
  private static final List<String> UNSAFE_TERMS =
      List.of(
          "질병",
          "치료",
          "약",
          "처방",
          "의학",
          "medical",
          "disease",
          "treatment",
          "medicine",
          "prescription");
  private final ReviewSummaryQueryRepository queries;
  private final ProductRepository products;
  private final ReviewSummaryAiClient ai;
  private final Clock clock;

  public ReviewSummaryService(
      ReviewSummaryQueryRepository queries,
      ProductRepository products,
      ReviewSummaryAiClient ai,
      Clock clock) {
    this.queries = queries;
    this.products = products;
    this.ai = ai;
    this.clock = clock;
  }

  @Transactional
  public ReviewSummaryResponse summary(long productId) {
    if (products.findPublicById(productId).isEmpty() || !queries.hasActiveBrand(productId))
      throw new ProductNotFoundException();
    List<ReviewRow> reviews = queries.latestReviews(productId).stream().map(this::toReviewRow).toList();
    long count = queries.visibleReviewCount(productId);
    BigDecimal average = queries.visibleAverageRating(productId);
    if (count < 3) return new ReviewSummaryResponse("INSUFFICIENT_REVIEWS", null, count, average);
    List<ReviewRow> sourceReviews =
        queries.allReviews(productId).stream().map(this::toReviewRow).toList();
    String fingerprint = fingerprint(sourceReviews);
    var cached = queries.cachedSummary(productId).orElse(null);
    if (cached != null && fingerprint.equals(cached.sourceFingerprint()))
      return new ReviewSummaryResponse("AVAILABLE", cached.summary(), count, average);
    String generated;
    try {
      generated =
          ai.summarize(
              reviews.stream()
                  .map(row -> new ReviewSummaryAiClient.ReviewInput(row.rating(), row.content()))
                  .toList());
    } catch (RuntimeException exception) {
      return new ReviewSummaryResponse("UNAVAILABLE", null, count, average);
    }
    if (!validAiText(generated))
      return new ReviewSummaryResponse("UNAVAILABLE", null, count, average);
    queries.saveSummary(productId, fingerprint, generated.trim(), Timestamp.from(clock.instant()));
    return new ReviewSummaryResponse("AVAILABLE", generated.trim(), count, average);
  }

  private ReviewRow toReviewRow(ReviewSummaryQueryRepository.ReviewRow row) {
    return new ReviewRow(row.id(), row.rating(), row.content(), row.updatedAt());
  }

  private boolean validAiText(String text) {
    if (text == null
        || text.isBlank()
        || text.codePointCount(0, text.length()) > 500
        || text.contains("<")
        || text.contains(">")) return false;
    if (text.codePoints()
        .anyMatch(
            codePoint ->
                Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r'))
      return false;
    if (text.codePoints()
        .noneMatch(
            codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL))
      return false;
    String lower = text.toLowerCase(java.util.Locale.ROOT);
    return UNSAFE_TERMS.stream().noneMatch(lower::contains);
  }

  record ReviewRow(long id, int rating, String content, Timestamp updatedAt) {}

  String fingerprint(List<ReviewRow> reviews) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (ReviewRow review : reviews) {
        byte[] value =
            (review.id()
                    + "\u0000"
                    + review.rating()
                    + "\u0000"
                    + review.content()
                    + "\u0000"
                    + review.updatedAt().getTime())
                .getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array());
        digest.update(value);
      }
      StringBuilder result = new StringBuilder(64);
      for (byte value : digest.digest()) result.append(String.format("%02x", value));
      return result.toString();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

}
