package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Feature-local persistence mapping; state transitions remain in the existing use cases. */
@Entity(name = "SubCreatedOrderAddon")
@Table(name = "subscription_order_addon_items")
@IdClass(SubscriptionOrderAddonItemId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionOrderAddonItemEntity {
  @Id
  @Column(name = "subscription_order_id")
  private long orderId;

  @Id
  @Column(name = "sku_id")
  private long skuId;

  private int quantity;

  @Column(name = "unit_price_krw", precision = 18, scale = 2)
  private BigDecimal price;

  public SubscriptionOrderAddonItemEntity(long orderId, long skuId, int quantity, BigDecimal price) {
    this.orderId = orderId;
    this.skuId = skuId;
    this.quantity = quantity;
    this.price = price;
  }
}
