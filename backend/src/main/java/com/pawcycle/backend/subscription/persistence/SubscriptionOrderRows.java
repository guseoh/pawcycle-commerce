package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;
import org.hibernate.type.descriptor.jdbc.LocalDateTimeJdbcType;

/** Subscription immutable order records; common order/payment/inventory entities are reused. */
final class SubscriptionOrderRows {
  private SubscriptionOrderRows() {}

  @Entity(name = "SubCreatedOrder") @Table(name = "subscription_orders")
  static class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "member_id") long memberId;
    @Column(name = "subscription_id") long subscriptionId;
    @Column(name = "schedule_id") long scheduleId;
    @Column(name = "effective_snapshot_id") long snapshotId;
    @Column(name = "source_plan_version_id") long planVersionId;
    @JdbcType(LocalDateJdbcType.class) @Column(name = "scheduled_date") LocalDate scheduledDate;
    @JdbcType(LocalDateTimeJdbcType.class) @Column(name = "processed_at") LocalDateTime processedAt;
    @Column(name = "package_total_krw", precision = 18, scale = 2) BigDecimal total;
    String status;
    protected Order() {}
  }

  @Entity(name = "SubCreatedOrderItem") @Table(name = "subscription_order_items")
  @IdClass(ItemId.class)
  static class Item {
    @Id @Column(name = "order_id") long orderId;
    @Id @Column(name = "sku_id") long skuId;
    int quantity;
    protected Item() {}
  }
  record ItemId(long orderId, long skuId) implements Serializable {}

  @Entity(name = "SubCreatedOrderAddon") @Table(name = "subscription_order_addon_items")
  @IdClass(AddonId.class)
  static class Addon {
    @Id @Column(name = "subscription_order_id") long orderId;
    @Id @Column(name = "sku_id") long skuId;
    int quantity;
    @Column(name = "unit_price_krw", precision = 18, scale = 2) BigDecimal price;
    protected Addon() {}
  }
  record AddonId(long orderId, long skuId) implements Serializable {}
}
