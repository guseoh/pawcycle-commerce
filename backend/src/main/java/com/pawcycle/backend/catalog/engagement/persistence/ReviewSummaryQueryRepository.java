package com.pawcycle.backend.catalog.engagement.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewSummaryQueryRepository {
  private final JdbcTemplate jdbc;

  public ReviewSummaryQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ReviewRow> latestReviews(long productId) {
    return jdbc.query(
        "SELECT id,rating,content,updated_at FROM reviews WHERE product_id=? AND visible=true"
            + " ORDER BY created_at DESC,id DESC LIMIT 30",
        (rs, n) -> new ReviewRow(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getTimestamp(4)),
        productId);
  }

  public long visibleReviewCount(long productId) {
    Long value =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reviews WHERE product_id=? AND visible=true",
            Long.class,
            productId);
    return value == null ? 0 : value;
  }

  public BigDecimal visibleAverageRating(long productId) {
    return jdbc.queryForObject(
        "SELECT AVG(rating) FROM reviews WHERE product_id=? AND visible=true",
        BigDecimal.class,
        productId);
  }

  public List<ReviewRow> allReviews(long productId) {
    return jdbc.query(
        "SELECT id,rating,content,updated_at FROM reviews WHERE product_id=? AND visible=true"
            + " ORDER BY id",
        (rs, n) -> new ReviewRow(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getTimestamp(4)),
        productId);
  }

  public Optional<CachedSummary> cachedSummary(long productId) {
    return jdbc.query(
            "SELECT source_fingerprint,summary FROM product_review_summaries WHERE product_id=?",
            (rs, n) -> new CachedSummary(rs.getString(1), rs.getString(2)),
            productId)
        .stream()
        .findFirst();
  }

  public void saveSummary(long productId, String fingerprint, String summary, Timestamp generatedAt) {
    jdbc.update(
        "INSERT INTO product_review_summaries(product_id,source_fingerprint,summary,generated_at)"
            + " VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " source_fingerprint=VALUES(source_fingerprint),summary=VALUES(summary),generated_at=VALUES(generated_at)",
        productId,
        fingerprint,
        summary,
        generatedAt);
  }

  public boolean hasActiveBrand(long productId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM products p JOIN brands b ON b.id=p.brand_id WHERE p.id=? AND"
                + " b.active=true",
            Integer.class,
            productId);
    return Integer.valueOf(1).equals(count);
  }

  public record ReviewRow(long id, int rating, String content, Timestamp updatedAt) {}

  public record CachedSummary(String sourceFingerprint, String summary) {}
}
