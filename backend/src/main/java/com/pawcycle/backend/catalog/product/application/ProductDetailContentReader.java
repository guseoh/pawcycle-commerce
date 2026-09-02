package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductDetailContentReader {
  private final JdbcTemplate jdbc;

  public ProductDetailContentReader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public List<ProductDetailSectionView> visibleSections(long productId) {
    return jdbc.query(
        """
        SELECT id,title,body,display_order,visible,created_at,updated_at
        FROM product_detail_sections
        WHERE product_id=? AND visible=true
        ORDER BY display_order ASC,id ASC
        """,
        (rs, rowNum) ->
            new ProductDetailSectionView(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getInt("display_order"),
                rs.getBoolean("visible"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()),
        productId);
  }

  @Transactional(readOnly = true)
  public ProductTrustView trust(long productId) {
    return jdbc.queryForObject(
        """
        SELECT AVG(CASE WHEN r.visible=true THEN r.rating END) AS average_rating,
               COALESCE(SUM(CASE WHEN r.visible=true THEN 1 ELSE 0 END),0) AS review_count,
               (SELECT COUNT(*) FROM product_questions q WHERE q.product_id=? AND q.visible=true) AS question_count
        FROM reviews r
        WHERE r.product_id=?
        """,
        (rs, rowNum) ->
            new ProductTrustView(
                rs.getBigDecimal("average_rating"),
                rs.getLong("review_count"),
                rs.getLong("question_count")),
        productId,
        productId);
  }

  public record ProductTrustView(BigDecimal averageRating, long reviewCount, long questionCount) {}
}
