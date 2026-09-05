package com.pawcycle.backend.catalog.product.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pawcycle.backend.catalog.product.persistence.ProductDetailQueryRepository;

@Service
public class ProductDetailContentReader {
  private final ProductDetailQueryRepository queries;

  public ProductDetailContentReader(ProductDetailQueryRepository queries) {
    this.queries = queries;
  }

  @Transactional(readOnly = true)
  public List<ProductDetailSectionView> visibleSections(long productId) {
    return queries.visibleSections(productId);
  }

  @Transactional(readOnly = true)
  public ProductTrustProjection trust(long productId) {
    return queries.trust(productId);
  }

}
