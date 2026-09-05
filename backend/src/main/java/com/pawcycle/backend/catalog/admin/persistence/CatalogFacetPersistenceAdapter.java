package com.pawcycle.backend.catalog.admin.persistence;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Owns the category/product facet compatibility query used by catalog mutations. */
@Repository
public class CatalogFacetPersistenceAdapter {
  private final ProductFacetValueRepository productFacets;

  public CatalogFacetPersistenceAdapter(ProductFacetValueRepository productFacets) {
    this.productFacets = productFacets;
  }

  @Transactional(readOnly = true)
  public boolean hasIncompatibleProductValues(long productId, long categoryId) {
    return productFacets.countIncompatible(productId, categoryId) > 0;
  }
}
