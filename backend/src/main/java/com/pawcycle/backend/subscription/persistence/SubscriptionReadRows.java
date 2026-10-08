package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcType;

/** Scalar-only read mappings. No associations, mutation API, or entity hydration in query results. */
final class SubscriptionReadRows {
  private SubscriptionReadRows() {}

  @Entity(name = "SubReadPet")
  @Table(name = "pets")
  @Immutable
  static class Pet {
    @Id Long id;
    @Column(name = "member_id") long memberId;
    String name;
    @Column(name = "pet_type") String petType;
    String breed;
    @Column(name = "weight_kg", precision = 5, scale = 2) BigDecimal weightKg;
    protected Pet() {}
  }

  @Entity(name = "SubReadPlan")
  @Table(name = "subscription_plans")
  @Immutable
  static class Plan {
    @Id Long id;
    String name;
    @Column(name = "current_plan_version_id") Long currentPlanVersionId;
    @Column(name = "target_pet_type") String targetPetType;
    @Column(name = "on_sale") boolean onSale;
    @Column(name = "sale_starts_on") LocalDate saleStartsOn;
    @Column(name = "sale_ends_on") LocalDate saleEndsOn;
    protected Plan() {}
  }

  @Entity(name = "SubReadPlanVersion")
  @Table(name = "plan_versions")
  @Immutable
  static class PlanVersion {
    @Id Long id;
    @Column(name = "plan_id") long planId;
    @Column(name = "package_price_krw") long packagePriceKrw;
    @Column(name = "is_migration_only") boolean migrationOnly;
    protected PlanVersion() {}
  }

  @Entity(name = "SubReadPlanItem")
  @Table(name = "plan_items")
  @IdClass(PlanItemId.class)
  @Immutable
  static class PlanItem {
    @Id @Column(name = "plan_version_id") long planVersionId;
    @Id @Column(name = "sku_id") long skuId;
    int quantity;
    protected PlanItem() {}
  }
  record PlanItemId(long planVersionId, long skuId) implements Serializable {}

  @Entity(name = "SubReadCycle")
  @Table(name = "plan_version_delivery_cycles")
  @IdClass(CycleId.class)
  @Immutable
  static class Cycle {
    @Id @Column(name = "plan_version_id") long planVersionId;
    @Id @Column(name = "delivery_cycle_weeks") int deliveryCycleWeeks;
    protected Cycle() {}
  }
  record CycleId(long planVersionId, int deliveryCycleWeeks) implements Serializable {}

  @Entity(name = "SubReadSubscription")
  @Table(name = "subscriptions")
  @Immutable
  static class Subscription {
    @Id Long id;
    @Column(name = "member_id") long memberId;
    String status;
    Long version;
    @Column(name = "pet_id") Long petId;
    @Column(name = "delivery_cycle_weeks") int deliveryCycleWeeks;
    @Column(name = "current_snapshot_id") Long currentSnapshotId;
    @Column(name = "runtime_managed") boolean runtimeManaged;
    protected Subscription() {}
  }

  @Entity(name = "SubReadSnapshot")
  @Table(name = "subscription_snapshots")
  @Immutable
  static class Snapshot {
    @Id Long id;
    @Column(name = "source_plan_version_id") long planVersionId;
    @Column(name = "package_total_krw") long packagePriceKrw;
    @Column(name = "delivery_cycle_weeks") int deliveryCycleWeeks;
    protected Snapshot() {}
  }

  @Entity(name = "SubReadSnapshotItem")
  @Table(name = "subscription_snapshot_items")
  @IdClass(SnapshotItemId.class)
  @Immutable
  static class SnapshotItem {
    @Id @Column(name = "snapshot_id") long snapshotId;
    @Id @Column(name = "sku_id") long skuId;
    int quantity;
    protected SnapshotItem() {}
  }
  record SnapshotItemId(long snapshotId, long skuId) implements Serializable {}

  @Entity(name = "SubReadSchedule")
  @Table(name = "subscription_schedules")
  @Immutable
  static class Schedule {
    @Id Long id;
    @Column(name = "subscription_id") long subscriptionId;
    @Column(name = "scheduled_date") LocalDate scheduledDate;
    String status;
    @Column(name = "hold_reason") String holdReason;
    @Column(name = "effective_snapshot_id") Long effectiveSnapshotId;
    protected Schedule() {}
  }

  @Entity(name = "SubReadOrder")
  @Table(name = "subscription_orders")
  @Immutable
  static class Order {
    @Id Long id;
    @Column(name = "schedule_id") long scheduleId;
    protected Order() {}
  }

  @Entity(name = "SubReadPendingChange")
  @Table(name = "pending_plan_changes")
  @Immutable
  static class PendingChange {
    @Id @Column(name = "subscription_id") Long subscriptionId;
    @Column(name = "snapshot_id") long snapshotId;
    @Column(name = "target_schedule_id") long targetScheduleId;
    protected PendingChange() {}
  }

  @Entity(name = "SubReadAddon")
  @Table(name = "subscription_schedule_addons")
  @IdClass(AddonId.class)
  @Immutable
  static class Addon {
    @Id @Column(name = "schedule_id") long scheduleId;
    @Id @Column(name = "sku_id") long skuId;
    int quantity;
    @Column(name = "unit_price_krw", precision = 18, scale = 2) BigDecimal unitPriceKrw;
    protected Addon() {}
  }
  record AddonId(long scheduleId, long skuId) implements Serializable {}

  @Entity(name = "SubReadCommandHistory")
  @Table(name = "subscription_command_history")
  @Immutable
  static class CommandHistory {
    @Id Long id;
    @Column(name = "subscription_id") long subscriptionId;
    @Column(name = "command_type") String commandType;
    @JdbcType(SubscriptionHistoryTimestampJdbcType.class)
    @Column(name = "occurred_at") Timestamp occurredAt;
    protected CommandHistory() {}
  }
}
