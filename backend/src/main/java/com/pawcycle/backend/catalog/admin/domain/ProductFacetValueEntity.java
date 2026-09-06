package com.pawcycle.backend.catalog.admin.domain;

import com.pawcycle.backend.catalog.product.domain.Product;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_facet_values")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ProductFacetValueEntity {
  @EmbeddedId private ProductFacetValueId id;

  @MapsId("productId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @MapsId("facetOptionId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "facet_option_id", nullable = false)
  private FacetOptionEntity facetOption;

  public ProductFacetValueEntity(Product product, FacetOptionEntity facetOption) {
    this.product = product;
    this.facetOption = facetOption;
    this.id = new ProductFacetValueId(product.getId(), facetOption.getId());
  }
}
