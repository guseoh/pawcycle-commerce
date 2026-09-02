package com.pawcycle.backend.catalog.sku.domain;

import com.pawcycle.backend.catalog.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skus")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Sku {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "sku_code", nullable = false, unique = true, length = 100)
  private String skuCode;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(name = "compare_at_price", precision = 12, scale = 2)
  private BigDecimal compareAtPrice;

  @Column(nullable = false)
  private boolean subscribable;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SkuStatus status;

  public Sku(
      Product product,
      String skuCode,
      String name,
      BigDecimal price,
      BigDecimal compareAtPrice,
      boolean subscribable,
      int displayOrder,
      SkuStatus status) {
    validatePriceRelation(price, compareAtPrice);
    this.product = product;
    this.skuCode = skuCode;
    this.name = name;
    this.price = price;
    this.compareAtPrice = compareAtPrice;
    this.subscribable = subscribable;
    this.displayOrder = displayOrder;
    this.status = status;
  }

  public Sku(
      Product product,
      String skuCode,
      String name,
      BigDecimal price,
      boolean subscribable,
      int displayOrder,
      SkuStatus status) {
    this(product, skuCode, name, price, null, subscribable, displayOrder, status);
  }

  public boolean isSubscribable() {
    return subscribable;
  }

  public void update(
      String name, BigDecimal price, Boolean subscribable, Integer displayOrder, SkuStatus status) {
    update(name, price, null, false, subscribable, displayOrder, status);
  }

  public void update(
      String name,
      BigDecimal price,
      BigDecimal compareAtPrice,
      boolean compareAtPricePresent,
      Boolean subscribable,
      Integer displayOrder,
      SkuStatus status) {
    BigDecimal nextPrice = price == null ? this.price : price;
    BigDecimal nextCompareAtPrice = compareAtPricePresent ? compareAtPrice : this.compareAtPrice;
    validatePriceRelation(nextPrice, nextCompareAtPrice);
    if (name != null) this.name = name;
    if (price != null) this.price = price;
    if (compareAtPricePresent) this.compareAtPrice = compareAtPrice;
    if (subscribable != null) this.subscribable = subscribable;
    if (displayOrder != null) this.displayOrder = displayOrder;
    if (status != null) this.status = status;
  }

  private static void validatePriceRelation(BigDecimal price, BigDecimal compareAtPrice) {
    if (price != null && compareAtPrice != null && compareAtPrice.compareTo(price) <= 0) {
      throw new IllegalArgumentException("compareAtPrice는 price보다 커야 합니다.");
    }
  }
}
