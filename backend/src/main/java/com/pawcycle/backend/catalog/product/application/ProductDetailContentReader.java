package com.pawcycle.backend.catalog.product.application;

import com.pawcycle.backend.catalog.engagement.persistence.ProductQuestionRepository;
import com.pawcycle.backend.catalog.engagement.persistence.ProductTrustQueryRepository;
import com.pawcycle.backend.catalog.product.persistence.ProductDetailSectionRow;
import com.pawcycle.backend.catalog.product.persistence.ProductDetailSectionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductDetailContentReader {
  private final ProductDetailSectionRepository sections;
  private final ProductTrustQueryRepository trust;
  private final ProductQuestionRepository questions;

  public ProductDetailContentReader(
      ProductDetailSectionRepository sections,
      ProductTrustQueryRepository trust,
      ProductQuestionRepository questions) {
    this.sections = sections;
    this.trust = trust;
    this.questions = questions;
  }

  @Transactional(readOnly = true)
  public List<ProductDetailSectionView> visibleSections(long productId) {
    return sections.findVisibleByProductId(productId).stream()
        .map(ProductDetailSectionRow::toView)
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductTrustProjection trust(long productId) {
    var summary = trust.findVisibleSummary(productId).orElse(null);
    return new ProductTrustProjection(
        summary == null ? null : summary.averageRating(),
        summary == null ? 0 : summary.reviewCount(),
        questions.countByProductIdAndVisibleTrue(productId));
  }
}
