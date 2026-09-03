package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductComparisonService {
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
  private final NativeQueryExecutor jdbc;
  private final ProductComparisonAiClient ai;

  public ProductComparisonService(NativeQueryExecutor jdbc, ProductComparisonAiClient ai) {
    this.jdbc = jdbc;
    this.ai = ai;
  }

  @Transactional(readOnly = true)
  public ProductComparisonResponse compare(List<Long> productIds) {
    if (productIds == null
        || productIds.size() < 2
        || productIds.size() > 3
        || productIds.stream().anyMatch(java.util.Objects::isNull)
        || productIds.stream().distinct().count() != productIds.size())
      throw new ProductComparisonException(400, "VALIDATION_FAILED", "상품은 서로 다른 2~3개를 선택해야 합니다.");
    List<ProductComparisonFacts> facts = productIds.stream().map(this::facts).toList();
    String summary = null;
    String status = "UNAVAILABLE";
    try {
      String generated = ai.compare(facts);
      if (validAiText(generated)) {
        summary = generated.trim();
        status = "AVAILABLE";
      }
    } catch (RuntimeException ignored) {
    }
    return new ProductComparisonResponse(facts, status, summary);
  }

  private ProductComparisonFacts facts(long productId) {
    Map<String, Object> row =
        jdbc
            .queryForList(
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
                WHERE p.id=? AND p.display_status='PUBLIC' AND c.active=true AND b.active=true\
                """,
                productId)
            .stream()
            .findFirst()
            .orElseThrow(
                () -> new ProductComparisonException(404, "PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
    List<String> facets =
        jdbc.query(
            "SELECT CONCAT(fd.`key`,':',fo.value) FROM product_facet_values pfv JOIN facet_options"
                + " fo ON fo.id=pfv.facet_option_id JOIN facet_definitions fd ON"
                + " fd.id=fo.facet_definition_id WHERE pfv.product_id=? ORDER BY"
                + " fd.id,fo.display_order,fo.id",
            (rs, n) -> rs.getString(1),
            productId);
    BigDecimal price = (BigDecimal) row.get("price"),
        compareAt = (BigDecimal) row.get("compare_at_price");
    Integer discount =
        price == null || compareAt == null
            ? null
            : compareAt
                .subtract(price)
                .multiply(BigDecimal.valueOf(100))
                .divide(compareAt, 0, RoundingMode.DOWN)
                .intValue();
    return new ProductComparisonFacts(
        productId,
        (String) row.get("name"),
        (String) row.get("thumbnail_url"),
        (String) row.get("brand_name"),
        (String) row.get("category_name"),
        price,
        compareAt,
        discount,
        (BigDecimal) row.get("average_rating"),
        ((Number) row.get("review_count")).longValue(),
        sqlBoolean(row.get("subscription_eligible")),
        sqlBoolean(row.get("purchasable")),
        facets);
  }

  private boolean sqlBoolean(Object value) {
    if (value instanceof Boolean booleanValue) return booleanValue;
    if (value instanceof Number number) return number.intValue() != 0;
    return false;
  }

  private boolean validAiText(String text) {
    if (text == null
        || text.isBlank()
        || text.codePointCount(0, text.length()) > 500
        || text.contains("<")
        || text.contains(">")
        || text.codePoints()
            .noneMatch(
                codePoint ->
                    Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL))
      return false;
    String lower = text.toLowerCase(java.util.Locale.ROOT);
    return UNSAFE_TERMS.stream().noneMatch(lower::contains);
  }

}
