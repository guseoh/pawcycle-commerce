package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Feature-local persistence mapping; state transitions remain in the existing use cases. */
@Entity(name = "SubCreatedOrderItem")
@Table(name = "subscription_order_items")
@IdClass(SubscriptionOrderItemId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionOrderItemEntity {
  @Id
  @Column(name = "order_id")
  private long orderId;

  @Id
  @Column(name = "sku_id")
  private long skuId;

  private int quantity;

  public SubscriptionOrderItemEntity(long orderId, long skuId, int quantity) {
    this.orderId = orderId;
    this.skuId = skuId;
    this.quantity = quantity;
  }
}
