package com.pawcycle.backend.catalog.product.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductComparisonQueryRepository {
  private final EntityManager entityManager;

  public ProductComparisonQueryRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public Optional<RawFacts> findFacts(long productId) {
    List<Tuple> rows =
        entityManager
            .createNativeQuery(
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
                WHERE p.id=:productId AND p.display_status='PUBLIC' AND c.active=true AND b.active=true
                """,
                Tuple.class)
            .setParameter("productId", productId)
            .getResultList();
    return rows.stream()
        .map(
            row ->
                new RawFacts(
                    longValue(row, "id"),
                    stringValue(row, "name"),
                    stringValue(row, "thumbnail_url"),
                    stringValue(row, "brand_name"),
                    stringValue(row, "category_name"),
                    decimalValue(row, "price"),
                    decimalValue(row, "compare_at_price"),
                    decimalValue(row, "average_rating"),
                    longValue(row, "review_count"),
                    booleanValue(row, "subscription_eligible"),
                    booleanValue(row, "purchasable")))
        .findFirst();
  }

  @Transactional(readOnly = true)
  public List<String> findFacets(long productId) {
    return entityManager
        .createNativeQuery(
            "SELECT CONCAT(fd.`key`,':',fo.value) FROM product_facet_values pfv JOIN facet_options fo ON fo.id=pfv.facet_option_id JOIN facet_definitions fd ON fd.id=fo.facet_definition_id WHERE pfv.product_id=:productId ORDER BY fd.id,fo.display_order,fo.id")
        .setParameter("productId", productId)
        .getResultList();
  }

  private static Object value(Tuple row, String alias) {
    return row.get(alias);
  }

  private static String stringValue(Tuple row, String alias) {
    return (String) value(row, alias);
  }

  private static long longValue(Tuple row, String alias) {
    return ((Number) value(row, alias)).longValue();
  }

  private static boolean booleanValue(Tuple row, String alias) {
    Object raw = value(row, alias);
    return raw instanceof Boolean booleanValue ? booleanValue : ((Number) raw).intValue() != 0;
  }

  private static BigDecimal decimalValue(Tuple row, String alias) {
    Number number = (Number) value(row, alias);
    return number == null ? null : number instanceof BigDecimal decimal ? decimal : new BigDecimal(number.toString());
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
