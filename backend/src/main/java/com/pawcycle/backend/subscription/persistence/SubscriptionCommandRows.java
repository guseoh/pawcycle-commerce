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
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;

/** Write records for tables without an existing mutable entity. Read projections stay independent. */
final class SubscriptionCommandRows {
  private SubscriptionCommandRows() {}

  @Entity(name = "SubCommandPet") @Table(name = "pets")
  static class Pet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "member_id") long memberId;
    String name;
    @Column(name = "pet_type") String petType;
    String breed;
    @Column(name = "weight_kg", precision = 5, scale = 2) BigDecimal weightKg;
    protected Pet() {}
  }

  @Entity(name = "SubCommandSubscription") @Table(name = "subscriptions")
  static class Subscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "member_id") long memberId;
    @Column(name = "sku_id") long skuId;
    int quantity;
    @Column(name = "delivery_cycle_weeks") int deliveryCycleWeeks;
    @JdbcType(LocalDateJdbcType.class) @Column(name = "created_date") LocalDate createdDate;
    @JdbcType(LocalDateJdbcType.class) @Column(name = "next_order_date") LocalDate nextOrderDate;
    @Column(name = "pet_id") Long petId;
    String status;
    long version;
    @Column(name = "current_snapshot_id") Long currentSnapshotId;
    @Column(name = "legacy_api_visible") boolean legacyApiVisible;
    @Column(name = "runtime_managed") boolean runtimeManaged;
    protected Subscription() {}
  }

  @Entity(name = "SubCommandSnapshot") @Table(name = "subscription_snapshots")
  static class Snapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "subscription_id") long subscriptionId;
    @Column(name = "source_plan_version_id") long planVersionId;
    @Column(name = "package_total_krw") long packagePriceKrw;
    @Column(name = "delivery_cycle_weeks") int deliveryCycleWeeks;
    protected Snapshot() {}
  }

  @Entity(name = "SubCommandSchedule") @Table(name = "subscription_schedules")
  static class Schedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "subscription_id") long subscriptionId;
    @JdbcType(LocalDateJdbcType.class) @Column(name = "scheduled_date") LocalDate scheduledDate;
    String status;
    @Column(name = "hold_reason") String holdReason;
    @Column(name = "effective_snapshot_id") Long effectiveSnapshotId;
    protected Schedule() {}
  }

  @Entity(name = "SubCommandPending") @Table(name = "pending_plan_changes")
  static class Pending {
    @Id @Column(name = "subscription_id") Long subscriptionId;
    @Column(name = "snapshot_id") long snapshotId;
    @Column(name = "target_schedule_id") long targetScheduleId;
    protected Pending() {}
  }

  @Entity(name = "SubCommandAddon") @Table(name = "subscription_schedule_addons")
  @IdClass(AddonId.class)
  static class Addon {
    @Id @Column(name = "schedule_id") long scheduleId;
    @Id @Column(name = "sku_id") long skuId;
    int quantity;
    @Column(name = "unit_price_krw", precision = 18, scale = 2) BigDecimal unitPriceKrw;
    protected Addon() {}
  }
  record AddonId(long scheduleId, long skuId) implements Serializable {}
}
