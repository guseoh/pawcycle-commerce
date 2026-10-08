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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.types.Projections;
import com.pawcycle.backend.catalog.sku.domain.QSku;
import com.pawcycle.backend.catalog.product.domain.QProduct;
import com.pawcycle.backend.catalog.category.domain.QCategory;
import com.pawcycle.backend.catalog.brand.domain.QBrand;
import com.pawcycle.backend.commerce.inventory.persistence.QInventoryEntity;

/** Typed JPA command preconditions; special JOIN/LIMIT locks retain the original MySQL SQL. */
class SubscriptionAggregateQueryPersistence {
  private final SubscriptionNativeSql nativeSql;
  private final SubscriptionReadQueries reads;
  private final JPAQueryFactory queries;
  private final QSubscriptionCommandRows_Subscription subscription = new QSubscriptionCommandRows_Subscription("subscription");
  private final QSubscriptionCommandRows_Schedule schedule = new QSubscriptionCommandRows_Schedule("schedule");

  SubscriptionAggregateQueryPersistence(SubscriptionReadQueries reads, EntityManager entities) {
    this.nativeSql = new SubscriptionNativeSql(entities);
    this.reads = reads;
    this.queries = new JPAQueryFactory(entities);
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
    var c = new QSubscriptionReadRows_Cycle("cycle");
    return queries.select(c.count()).from(c).where(c.planVersionId.eq(versionId), c.deliveryCycleWeeks.eq(cycle)).fetchOne() > 0;
  }

public boolean planContainsSku(long versionId, long skuId) {
    var i = new QSubscriptionReadRows_PlanItem("item");
    return queries.select(i.count()).from(i).where(i.planVersionId.eq(versionId), i.skuId.eq(skuId)).fetchOne() > 0;
  }

public boolean scheduleAddonConflicts(long scheduleId, long versionId) {
    var a = new QSubscriptionReadRows_Addon("addon");
    var i = new QSubscriptionReadRows_PlanItem("item");
    return queries.select(a.count()).from(a).join(i).on(i.skuId.eq(a.skuId))
        .where(a.scheduleId.eq(scheduleId), i.planVersionId.eq(versionId)).fetchOne() > 0;
  }

public AddonSkuProjection findEligibleAddonSku(long skuId) {
    var sku = new QSku("sku");
    var product = new QProduct("product");
    var category = new QCategory("category");
    var brand = new QBrand("brand");
    var inventory = new QInventoryEntity("inventory");
    var eligible = sku.status.eq(com.pawcycle.backend.catalog.sku.domain.SkuStatus.ACTIVE).and(product.status.eq(com.pawcycle.backend.catalog.product.domain.ProductStatus.PUBLIC))
        .and(category.active.isTrue()).and(brand.active.isTrue()).and(inventory.availableQuantity.coalesce(0).gt(0));
    return Optional.ofNullable(queries.select(Projections.constructor(AddonSkuProjection.class,
            sku.id, product.id, product.name, sku.name, sku.price, eligible))
        .from(sku).join(sku.product, product).join(product.category, category).join(brand).on(brand.id.eq(product.brandId))
        .leftJoin(inventory).on(inventory.skuId.eq(sku.id)).where(sku.id.eq(skuId)).fetchOne())
        .orElseThrow(() -> new SubscriptionApiException(404, "ADDON_NOT_FOUND", "Add-on을 찾을 수 없습니다."));
  }

public SubscriptionProjection lockOwnedSubscription(long memberId, long subscriptionId) {
    return Optional.ofNullable(queries.select(Projections.constructor(SubscriptionProjection.class, subscription.id, subscription.memberId, subscription.status, subscription.version, subscription.petId, subscription.deliveryCycleWeeks, subscription.currentSnapshotId)).from(subscription)
        .where(subscription.id.eq(subscriptionId), subscription.memberId.eq(memberId), subscription.runtimeManaged.isTrue())
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne())
        .orElseThrow(() -> new SubscriptionApiException(404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다."));
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
    return nativeSql.query(
            "SELECT schedule.id,schedule.scheduled_date FROM subscription_schedules schedule LEFT"
                + " JOIN subscription_orders existing_order ON existing_order.schedule_id=schedule.id WHERE schedule.subscription_id=? AND"
                + " (schedule.status='SCHEDULED' OR (schedule.status='HELD' AND schedule.hold_reason='ORDER_STOCK_UNAVAILABLE')) AND existing_order.id IS NULL"
                + " ORDER BY schedule.scheduled_date,schedule.id LIMIT 1 FOR UPDATE", ScheduleProjection.class, subscriptionId)
        .addScalar("id", Long.class).addScalar("scheduled_date", LocalDate.class)
        .getResultList().stream().findFirst()
        .orElseThrow(() -> new SubscriptionApiException(409, "SUBSCRIPTION_COMMAND_NOT_ALLOWED", "다음 Schedule이 없습니다."));
  }

public Optional<PendingSubscriptionChange> findPendingChange(long subscriptionId) {
    return reads.pendingChange(subscriptionId);
  }

public int scheduleAddonCount(long scheduleId) {
    return reads.addonCount(scheduleId);
  }

public boolean hasScheduleAddon(long scheduleId, long skuId) {
    var a = new QSubscriptionReadRows_Addon("addon");
    return queries.select(a.count()).from(a).where(a.scheduleId.eq(scheduleId), a.skuId.eq(skuId)).fetchOne() > 0;
  }

public List<ScheduleAddonProjection> findScheduleAddons(long scheduleId) {
    return reads.addons(scheduleId);
  }

public boolean scheduleDateTaken(long subscriptionId, LocalDate date, long excludedScheduleId) {
    return queries.select(schedule.count()).from(schedule).where(schedule.subscriptionId.eq(subscriptionId),
        schedule.scheduledDate.eq(date), schedule.id.ne(excludedScheduleId)).fetchOne() > 0;
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
    var p = new QSubscriptionReadRows_PendingChange("pending");
    return Optional.ofNullable(queries.select(p.snapshotId).from(p).where(p.subscriptionId.eq(subscriptionId)).fetchOne());
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
    return queries.select(subscription.id).from(subscription).where(subscription.runtimeManaged.isTrue(), subscription.status.eq("ACTIVE"))
        .orderBy(subscription.id.asc()).fetch();
  }

public Optional<SubscriptionProjection> lockActiveSubscription(long subscriptionId) {
    return Optional.ofNullable(queries.select(Projections.constructor(SubscriptionProjection.class, subscription.id, subscription.memberId, subscription.status, subscription.version, subscription.petId, subscription.deliveryCycleWeeks, subscription.currentSnapshotId)).from(subscription)
        .where(subscription.id.eq(subscriptionId), subscription.runtimeManaged.isTrue(), subscription.status.eq("ACTIVE"))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne());
  }

public boolean hasUnprocessedDueSchedule(long subscriptionId, LocalDate today) {
    return !nativeSql.query(
            "SELECT schedule.id FROM subscription_schedules schedule LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id=schedule.id WHERE"
                + " schedule.subscription_id=? AND schedule.status='SCHEDULED' AND schedule.scheduled_date<=? AND existing_order.id IS NULL ORDER BY"
                + " schedule.scheduled_date,schedule.id LIMIT 1 FOR UPDATE", Long.class, subscriptionId, today)
        .getResultList().isEmpty();
  }

public List<ScheduleProjection> futureSchedulesForUpdate(long subscriptionId, LocalDate today) {
    return queries.select(Projections.constructor(ScheduleProjection.class, schedule.id, schedule.scheduledDate))
        .from(schedule).where(schedule.subscriptionId.eq(subscriptionId), schedule.status.eq("SCHEDULED"), schedule.scheduledDate.gt(today))
        .orderBy(schedule.scheduledDate.asc(), schedule.id.asc()).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
  }

public Optional<ProcessedScheduleProjection> lastProcessedSchedule(long subscriptionId) {
    var order = new QSubscriptionOrderRows_Order("processed");
    var snapshot = new QSubscriptionCommandRows_Snapshot("snapshot");
    return Optional.ofNullable(queries.select(Projections.constructor(ProcessedScheduleProjection.class, order.scheduledDate, snapshot.deliveryCycleWeeks))
        .from(order).join(snapshot).on(snapshot.id.eq(order.snapshotId)).where(order.subscriptionId.eq(subscriptionId))
        .orderBy(order.scheduledDate.desc(), order.id.desc()).fetchFirst());
  }

public boolean scheduleExists(long subscriptionId, LocalDate date) {
    return queries.select(schedule.count()).from(schedule)
        .where(schedule.subscriptionId.eq(subscriptionId), schedule.scheduledDate.eq(date)).fetchOne() > 0;
  }
}
