package com.pawcycle.backend.catalog.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_option_values")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ProductOptionValueEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "option_group_id", nullable = false)
  private ProductOptionGroupEntity optionGroup;

  @Column(nullable = false, length = 100)
  private String value;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  public ProductOptionValueEntity(ProductOptionGroupEntity optionGroup, String value, int displayOrder) {
    this.optionGroup = optionGroup;
    this.value = value;
    this.displayOrder = displayOrder;
  }

  public void update(String value, int displayOrder) {
    this.value = value;
    this.displayOrder = displayOrder;
  }
}
