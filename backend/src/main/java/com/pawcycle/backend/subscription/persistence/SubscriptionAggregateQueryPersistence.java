package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.api.SubscriptionApiException;
import com.pawcycle.backend.subscription.persistence.projection.AddonSkuProjection;
import com.pawcycle.backend.subscription.persistence.projection.CommandHistoryProjection;
import com.pawcycle.backend.subscription.persistence.projection.NextDeliveryProjection;
import com.pawcycle.backend.subscription.persistence.projection.PageProjection;
import com.pawcycle.backend.subscription.persistence.projection.PendingSubscriptionChange;
import com.pawcycle.backend.subscription.persistence.projection.PetProjection;
import com.pawcycle.backend.subscription.persistence.projection.PlanVersionProjection;
import com.pawcycle.backend.subscription.persistence.projection.ProcessedScheduleProjection;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleAddonProjection;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleProjection;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleViewProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionItemDetailProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionItemProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionSnapshot;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionSnapshotBase;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Compatibility query facade: typed JPA reads; unchanged T09 JDBC command preconditions/locks. */
class SubscriptionAggregateQueryPersistence {
  private final JdbcTemplate jdbc;
  private final SubscriptionReadQueries reads;

  SubscriptionAggregateQueryPersistence(JdbcTemplate jdbc, SubscriptionReadQueries reads) {
    this.jdbc = jdbc;
    this.reads = reads;
  }

public PetProjection findOwnedPet(long memberId, long petId) {
    return reads.ownedPet(memberId, petId)
        .orElseThrow(() -> new SubscriptionApiException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다."));
  }

public PlanVersionProjection findPlanVersion(long versionId) {
    return reads.planVersion(versionId)
        .orElseThrow(() -> new SubscriptionApiException(404, "PLAN_VERSION_NOT_FOUND", "PlanVersion을 찾을 수 없습니다."));
  }

public boolean deliveryCycleAllowed(long versionId, int cycle) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM plan_version_delivery_cycles WHERE plan_version_id=? AND"
                + " delivery_cycle_weeks=?",
            Integer.class,
            versionId,
            cycle)
        > 0;
  }

public boolean planContainsSku(long versionId, long skuId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM plan_items WHERE plan_version_id=? AND sku_id=?",
            Integer.class,
            versionId,
            skuId)
        > 0;
  }

public boolean scheduleAddonConflicts(long scheduleId, long versionId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedule_addons addon JOIN plan_items item ON"
                + " item.sku_id=addon.sku_id WHERE addon.schedule_id=? AND item.plan_version_id=?",
            Integer.class,
            scheduleId,
            versionId)
        > 0;
  }

public AddonSkuProjection findEligibleAddonSku(long skuId) {
    return one(
            "SELECT sku.id sku_id,product.id product_id,product.name product_name,sku.name"
                + " sku_name,sku.price,product.display_status,category.active"
                + " category_active,brand.active brand_active,sku.status"
                + " sku_status,COALESCE(inventory.available_quantity,0) available_quantity FROM"
                + " skus sku JOIN products product ON product.id=sku.product_id JOIN categories"
                + " category ON category.id=product.category_id JOIN brands brand ON"
                + " brand.id=product.brand_id LEFT JOIN inventories inventory ON"
                + " inventory.sku_id=sku.id WHERE sku.id=?",
            skuId)
        .map(
            row ->
                new AddonSkuProjection(
                    skuId,
                    longValue(row, "product_id"),
                    (String) row.get("product_name"),
                    (String) row.get("sku_name"),
                    (java.math.BigDecimal) row.get("price"),
                    "ACTIVE".equals(row.get("sku_status"))
                        && "PUBLIC".equals(row.get("display_status"))
                        && Boolean.TRUE.equals(row.get("category_active"))
                        && Boolean.TRUE.equals(row.get("brand_active"))
                        && intValue(row, "available_quantity") > 0))
        .orElseThrow(() -> new SubscriptionApiException(404, "ADDON_NOT_FOUND", "Add-on을 찾을 수 없습니다."));
  }

public SubscriptionProjection lockOwnedSubscription(long memberId, long subscriptionId) {
    return one(
            "SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id"
                + " FROM subscriptions WHERE id=? AND member_id=? AND runtime_managed=true FOR UPDATE",
            subscriptionId,
            memberId)
        .map(this::subscription)
        .orElseThrow(
            () -> new SubscriptionApiException(404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다."));
  }

public SubscriptionProjection findOwnedSubscription(long memberId, long subscriptionId) {
    return reads.ownedSubscription(memberId, subscriptionId)
        .orElseThrow(() -> new SubscriptionApiException(404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다."));
  }

public PageProjection<PetProjection> findPets(long memberId, int page, int size) {
    return reads.pets(memberId, page, size);
  }

public PageProjection<PlanVersionProjection> findSalePlanVersions(
      String petType, LocalDate today, int page, int size) {
    return reads.salePlans(petType, today, page, size);
  }

public List<SubscriptionItemProjection> findPlanItems(long versionId) {
    return reads.planItems(List.of(versionId)).getOrDefault(versionId, List.of());
  }

public List<Integer> findDeliveryCycles(long versionId) {
    return reads.cycles(List.of(versionId)).getOrDefault(versionId, List.of());
  }

public Map<Long, List<SubscriptionItemProjection>> findPlanItems(List<Long> versionIds) {
    return reads.planItems(versionIds);
  }

public Map<Long, List<Integer>> findDeliveryCycles(List<Long> versionIds) {
    return reads.cycles(versionIds);
  }

public SubscriptionSnapshot findSnapshot(long snapshotId) {
    return reads.snapshot(snapshotId);
  }

public ScheduleProjection lockNextScheduled(long subscriptionId) {
    return one(
            "SELECT schedule.id,schedule.scheduled_date FROM subscription_schedules schedule LEFT"
                + " JOIN subscription_orders existing_order ON"
                + " existing_order.schedule_id=schedule.id WHERE schedule.subscription_id=? AND"
                + " (schedule.status='SCHEDULED' OR (schedule.status='HELD' AND"
                + " schedule.hold_reason='ORDER_STOCK_UNAVAILABLE')) AND existing_order.id IS NULL"
                + " ORDER BY schedule.scheduled_date,schedule.id LIMIT 1 FOR UPDATE",
            subscriptionId)
        .map(
            row ->
                new ScheduleProjection(
                    longValue(row, "id"), date(row.get("scheduled_date"))))
        .orElseThrow(
            () ->
                new SubscriptionApiException(409, "SUBSCRIPTION_COMMAND_NOT_ALLOWED", "다음 Schedule이 없습니다."));
  }

public Optional<PendingSubscriptionChange> findPendingChange(long subscriptionId) {
    return reads.pendingChange(subscriptionId);
  }

public int scheduleAddonCount(long scheduleId) {
    return reads.addonCount(scheduleId);
  }

public boolean hasScheduleAddon(long scheduleId, long skuId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedule_addons WHERE schedule_id=? AND sku_id=?",
            Integer.class,
            scheduleId,
            skuId)
        > 0;
  }

public List<ScheduleAddonProjection> findScheduleAddons(long scheduleId) {
    return reads.addons(scheduleId);
  }

public boolean scheduleDateTaken(long subscriptionId, LocalDate date, long excludedScheduleId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                + " scheduled_date=? AND id<>?",
            Integer.class,
            subscriptionId,
            date,
            excludedScheduleId)
        > 0;
  }

private SubscriptionProjection subscription(Map<String, Object> row) {
    return new SubscriptionProjection(
        longValue(row, "id"),
        longValue(row, "member_id"),
        (String) row.get("status"),
        longValue(row, "version"),
        row.get("pet_id") == null ? null : longValue(row, "pet_id"),
        intValue(row, "delivery_cycle_weeks"),
        longValue(row, "current_snapshot_id"));
  }

private LocalDate date(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDate localDate) return localDate;
    if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime().toLocalDate();
    }
    throw new IllegalArgumentException("Unsupported date value type: " + value.getClass().getName());
  }

public PageProjection<SubscriptionProjection> findSubscriptions(
      long memberId, int page, int size) {
    return reads.subscriptions(memberId, page, size);
  }

public Map<Long, PetProjection> findOwnedPets(long memberId, List<Long> ids) {
    return reads.ownedPets(memberId, ids);
  }

public Map<Long, SubscriptionSnapshotBase> findSnapshots(List<Long> ids) {
    return reads.snapshots(ids);
  }

public Map<Long, List<SubscriptionItemProjection>> findSnapshotItems(List<Long> snapshotIds) {
    return reads.snapshotItems(snapshotIds);
  }

public Map<Long, LocalDate> findNextSchedules(List<Long> subscriptionIds, LocalDate today) {
    return reads.nextSchedules(subscriptionIds, today);
  }

public Optional<LocalDate> findNextSchedule(long subscriptionId, LocalDate today) {
    return reads.nextSchedule(subscriptionId, today);
  }

public Optional<Long> findPendingSnapshotId(long subscriptionId) {
    return one(
            "SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?", subscriptionId)
        .map(row -> longValue(row, "snapshot_id"));
  }

public Optional<NextDeliveryProjection> findNextDeliverySchedule(long subscriptionId) {
    return reads.nextDelivery(subscriptionId);
  }

public List<SubscriptionItemDetailProjection> findSnapshotItemDetails(long snapshotId) {
    return reads.snapshotItemDetails(snapshotId);
  }

public PageProjection<ScheduleViewProjection> findScheduleViews(
      long subscriptionId, int page, int size) {
    return reads.schedules(subscriptionId, page, size);
  }

public PageProjection<CommandHistoryProjection> findCommandHistory(
      long subscriptionId, int page, int size) {
    return reads.history(subscriptionId, page, size);
  }

public List<Long> activeSubscriptionIds() {
    return jdbc.queryForList(
        "SELECT id FROM subscriptions WHERE runtime_managed=true AND status='ACTIVE' ORDER BY id",
        Long.class);
  }

public Optional<SubscriptionProjection> lockActiveSubscription(long subscriptionId) {
    return one(
            "SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id"
                + " FROM subscriptions WHERE id=? AND runtime_managed=true AND status='ACTIVE' FOR"
                + " UPDATE",
            subscriptionId)
        .map(this::subscription);
  }

public boolean hasUnprocessedDueSchedule(long subscriptionId, LocalDate today) {
    return !jdbc.queryForList(
            "SELECT schedule.id FROM subscription_schedules schedule LEFT JOIN subscription_orders"
                + " existing_order ON existing_order.schedule_id=schedule.id WHERE"
                + " schedule.subscription_id=? AND schedule.status='SCHEDULED' AND"
                + " schedule.scheduled_date<=? AND existing_order.id IS NULL ORDER BY"
                + " schedule.scheduled_date,schedule.id LIMIT 1 FOR UPDATE",
            subscriptionId,
            today)
        .isEmpty();
  }

public List<ScheduleProjection> futureSchedulesForUpdate(long subscriptionId, LocalDate today) {
    return jdbc.query(
        "SELECT id,scheduled_date FROM subscription_schedules WHERE subscription_id=? AND"
            + " status='SCHEDULED' AND scheduled_date>? ORDER BY scheduled_date,id FOR UPDATE",
        (rs, rowNum) ->
            new ScheduleProjection(
                rs.getLong("id"), rs.getDate("scheduled_date").toLocalDate()),
        subscriptionId,
        today);
  }

public Optional<ProcessedScheduleProjection> lastProcessedSchedule(long subscriptionId) {
    return one(
            "SELECT orders.scheduled_date,snapshot.delivery_cycle_weeks FROM subscription_orders"
                + " orders JOIN subscription_snapshots snapshot ON"
                + " snapshot.id=orders.effective_snapshot_id WHERE orders.subscription_id=? ORDER"
                + " BY orders.scheduled_date DESC,orders.id DESC LIMIT 1",
            subscriptionId)
        .map(
            row ->
                new ProcessedScheduleProjection(
                    date(row.get("scheduled_date")), intValue(row, "delivery_cycle_weeks")));
  }

public boolean scheduleExists(long subscriptionId, LocalDate date) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                + " scheduled_date=?",
            Integer.class,
            subscriptionId,
            date)
        > 0;
  }

private Optional<Map<String, Object>> one(String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

private long longValue(Map<String, Object> r, String k) {
    return ((Number) r.get(k)).longValue();
  }

private int intValue(Map<String, Object> r, String k) {
    return ((Number) r.get(k)).intValue();
  }

}
