package com.pawcycle.backend.catalog.admin.domain;

import com.pawcycle.backend.catalog.sku.domain.Sku;
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
@Table(name = "sku_option_values")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class SkuOptionValueEntity {
  @EmbeddedId private SkuOptionValueId id;

  @MapsId("skuId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sku_id", nullable = false)
  private Sku sku;

  @MapsId("optionValueId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "option_value_id", nullable = false)
  private ProductOptionValueEntity optionValue;

  public SkuOptionValueEntity(Sku sku, ProductOptionValueEntity optionValue) {
    this.sku = sku;
    this.optionValue = optionValue;
    this.id = new SkuOptionValueId(sku.getId(), optionValue.getId());
  }
}
