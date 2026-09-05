package com.pawcycle.backend.catalog.product.application;

import com.pawcycle.backend.catalog.product.persistence.ProductDiscoveryQueryRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductDiscoveryReader {
  private final ProductDiscoveryQueryRepository queries;

  public ProductDiscoveryReader(ProductDiscoveryQueryRepository queries) {
    this.queries = queries;
  }

  @Transactional(readOnly = true)
  public ProductListView read(
      String q, String petType, String category, int page, int size, ProductSort sort) {
    return queries.read(q, petType, category, page, size, sort);
  }

  @Transactional(readOnly = true)
  public ProductListView read(
      String q,
      String petType,
      String category,
      String subcategory,
      String brand,
      List<String> facets,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      Boolean subscribable,
      Boolean purchasable,
      int page,
      int size,
      ProductSort sort) {
    return queries.read(
        q,
        petType,
        category,
        subcategory,
        brand,
        facets,
        minPrice,
        maxPrice,
        subscribable,
        purchasable,
        page,
        size,
        sort);
  }

  @Transactional(readOnly = true)
  public List<ProductDetailSkuRow> readDetailSkus(Long productId) {
    return queries.readDetailSkus(productId);
  }

  @Transactional(readOnly = true)
  public ProductDetailSupplement readDetailSupplement(Long productId) {
    return queries.readDetailSupplement(productId);
  }
}
