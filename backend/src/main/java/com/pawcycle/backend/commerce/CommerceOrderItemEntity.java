package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "order_items")
public class CommerceOrderItemEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false)
  Long orderId;

  @Column(name = "sku_id", nullable = false)
  Long skuId;

  @Column(nullable = false)
  int quantity;

  @Column(name = "line_amount", precision = 18, scale = 2)
  BigDecimal lineAmount;

  @Column(name = "snapshot_quality", nullable = false)
  String snapshotQuality;

  @Column(name = "sku_code_snapshot")
  String skuCodeSnapshot;

  @Column(name = "product_name_snapshot")
  String productNameSnapshot;

  @Column(name = "sku_name_snapshot")
  String skuNameSnapshot;

  @Column(name = "unit_price", precision = 18, scale = 2)
  BigDecimal unitPrice;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", insertable = false, updatable = false)
  CommerceOrderEntity order;

  protected CommerceOrderItemEntity() {}

  public CommerceOrderItemEntity(
      long orderId,
      long skuId,
      String skuCodeSnapshot,
      String productNameSnapshot,
      String skuNameSnapshot,
      BigDecimal unitPrice,
      int quantity) {
    this.orderId = orderId;
    this.skuId = skuId;
    this.snapshotQuality = "FULL";
    this.skuCodeSnapshot = skuCodeSnapshot;
    this.productNameSnapshot = productNameSnapshot;
    this.skuNameSnapshot = skuNameSnapshot;
    this.unitPrice = unitPrice;
    this.quantity = quantity;
    this.lineAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
  }

  public long getSkuId() {
    return skuId;
  }

  public int getQuantity() {
    return quantity;
  }

  public String getProductNameSnapshot() {
    return productNameSnapshot;
  }

  public String getSkuCodeSnapshot() {
    return skuCodeSnapshot;
  }

  public String getSkuNameSnapshot() {
    return skuNameSnapshot;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getLineAmount() {
    return lineAmount;
  }
}
