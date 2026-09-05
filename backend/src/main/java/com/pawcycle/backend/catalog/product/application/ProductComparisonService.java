package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import com.pawcycle.backend.catalog.product.persistence.ProductComparisonQueryRepository;
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
  private final ProductComparisonQueryRepository queries;
  private final ProductComparisonAiClient ai;

  public ProductComparisonService(ProductComparisonQueryRepository queries, ProductComparisonAiClient ai) {
    this.queries = queries;
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
    ProductComparisonQueryRepository.RawFacts row =
        queries
            .findFacts(productId)
            .orElseThrow(
                () -> new ProductComparisonException(404, "PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
    List<String> facets = queries.findFacets(productId);
    BigDecimal price = row.price(), compareAt = row.compareAtPrice();
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
        row.name(),
        row.thumbnailUrl(),
        row.brandName(),
        row.categoryName(),
        price,
        compareAt,
        discount,
        row.averageRating(),
        row.reviewCount(),
        row.subscriptionEligible(),
        row.purchasable(),
        facets);
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
