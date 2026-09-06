package com.pawcycle.backend.catalog.admin.domain;

import com.pawcycle.backend.catalog.product.domain.Product;
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
@Table(name = "product_option_groups")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ProductOptionGroupEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  public ProductOptionGroupEntity(Product product, String name, int displayOrder) {
    this.product = product;
    this.name = name;
    this.displayOrder = displayOrder;
  }

  public void update(String name, int displayOrder) {
    this.name = name;
    this.displayOrder = displayOrder;
  }
}
