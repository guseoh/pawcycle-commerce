package com.pawcycle.backend.catalog.product.persistence;

import com.pawcycle.backend.catalog.product.application.ProductComparisonFacts;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductComparisonQueryRepository {
  private final JdbcTemplate jdbc;

  public ProductComparisonQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<RawFacts> findFacts(long productId) {
    return jdbc.query(
            """
            SELECT p.id,p.name,COALESCE(main_image.image_url,p.thumbnail_url) thumbnail_url,b.name brand_name,c.name category_name,
                   (SELECT s.price FROM skus s WHERE s.product_id=p.id AND s.status='ACTIVE' ORDER BY s.price,s.id LIMIT 1) price,
                   (SELECT s.compare_at_price FROM skus s WHERE s.product_id=p.id AND s.status='ACTIVE' ORDER BY s.price,s.id LIMIT 1) compare_at_price,
                   (SELECT AVG(r.rating) FROM reviews r WHERE r.product_id=p.id AND r.visible=true) average_rating,
                   (SELECT COUNT(*) FROM reviews r WHERE r.product_id=p.id AND r.visible=true) review_count,
                   EXISTS(SELECT 1 FROM skus s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.subscribable=true) subscription_eligible,
                   EXISTS(SELECT 1 FROM skus s JOIN inventories i ON i.sku_id=s.id WHERE s.product_id=p.id AND s.status='ACTIVE' AND i.available_quantity>0) purchasable
            FROM products p JOIN categories c ON c.id=p.category_id JOIN brands b ON b.id=p.brand_id
            LEFT JOIN product_images main_image ON main_image.product_id=p.id AND main_image.image_type='MAIN'
            WHERE p.id=? AND p.display_status='PUBLIC' AND c.active=true AND b.active=true
            """,
            (rs, rowNum) ->
                new RawFacts(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("thumbnail_url"),
                    rs.getString("brand_name"),
                    rs.getString("category_name"),
                    rs.getBigDecimal("price"),
                    rs.getBigDecimal("compare_at_price"),
                    rs.getBigDecimal("average_rating"),
                    rs.getLong("review_count"),
                    sqlBoolean(rs.getObject("subscription_eligible")),
                    sqlBoolean(rs.getObject("purchasable"))),
            productId)
        .stream()
        .findFirst();
  }

  public List<String> findFacets(long productId) {
    return jdbc.query(
        "SELECT CONCAT(fd.`key`,':',fo.value) FROM product_facet_values pfv JOIN facet_options"
            + " fo ON fo.id=pfv.facet_option_id JOIN facet_definitions fd ON"
            + " fd.id=fo.facet_definition_id WHERE pfv.product_id=? ORDER BY"
            + " fd.id,fo.display_order,fo.id",
        (rs, n) -> rs.getString(1),
        productId);
  }

  private static boolean sqlBoolean(Object value) {
    if (value instanceof Boolean booleanValue) return booleanValue;
    if (value instanceof Number number) return number.intValue() != 0;
    return false;
  }

  public record RawFacts(
      long productId,
      String name,
      String thumbnailUrl,
      String brandName,
      String categoryName,
      BigDecimal price,
      BigDecimal compareAtPrice,
      BigDecimal averageRating,
      long reviewCount,
      boolean subscriptionEligible,
      boolean purchasable) {}
}
