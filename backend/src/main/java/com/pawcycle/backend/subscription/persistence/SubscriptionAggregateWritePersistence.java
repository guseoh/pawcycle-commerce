package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.api.SubscriptionApiException;
import java.time.LocalDate;
import jakarta.persistence.EntityManager;
import com.pawcycle.backend.commerce.notification.persistence.QNotificationEntity;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;

/** JPA command writes with explicitly retained atomic MySQL operations. */
class SubscriptionAggregateWritePersistence {
  private final SubscriptionNativeSql nativeSql;
  private final EntityManager entities;
  private final JPAQueryFactory queries;
  private final QSubscriptionCommandRows_Subscription subscription = new QSubscriptionCommandRows_Subscription("subscription");
  private final QSubscriptionCommandRows_Schedule schedule = new QSubscriptionCommandRows_Schedule("schedule");
  private final QSubscriptionCommandRows_Pending pending = new QSubscriptionCommandRows_Pending("pending");
  private final QSubscriptionCommandRows_Addon addon = new QSubscriptionCommandRows_Addon("addon");

  SubscriptionAggregateWritePersistence(EntityManager entities) {
    this.nativeSql = new SubscriptionNativeSql(entities);
    this.entities = entities;
    this.queries = new JPAQueryFactory(entities);
  }

public long insertSubscription(
      long memberId, long versionId, int cycle, long petId, LocalDate created, LocalDate next) {
    var row = new SubscriptionCommandRows.Subscription();
    row.memberId = memberId;
    row.skuId = firstSku(versionId);
    row.quantity = 1;
    row.deliveryCycleWeeks = cycle;
    row.createdDate = created;
    row.nextOrderDate = next;
    row.petId = petId;
    row.status = "ACTIVE";
    row.runtimeManaged = true;
    insert(row);
    return row.id;
  }

public void setCurrentSnapshot(long subscriptionId, long snapshotId) {
    queries.update(subscription).set(subscription.currentSnapshotId, snapshotId).where(subscription.id.eq(subscriptionId)).execute();
  }

public void insertScheduled(long subscriptionId, LocalDate date) {
    insertScheduledAndReturnId(subscriptionId, date);
  }

public long createSnapshot(long subscriptionId, long versionId, int cycle, long price) {
    return snapshot(subscriptionId, versionId, cycle, price);
  }

public long insertPet(long memberId, String name, String petType) {
    var row = new SubscriptionCommandRows.Pet();
    row.memberId = memberId;
    row.name = name;
    row.petType = petType;
    insert(row);
    return row.id;
  }

public void updatePet(
      long memberId,
      long petId,
      String name,
      boolean namePresent,
      String breed,
      boolean breedPresent,
      java.math.BigDecimal weightKg,
      boolean weightPresent) {
    var pet = new QSubscriptionCommandRows_Pet("pet");
    var update = queries.update(pet).where(pet.id.eq(petId), pet.memberId.eq(memberId));
    if (namePresent) update.set(pet.name, name);
    if (breedPresent) update.set(pet.breed, breed);
    if (weightPresent) update.set(pet.weightKg, weightKg);
    if (update.execute() != 1) throw new SubscriptionApiException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다.");
  }

public void replacePendingPlanChange(long subscriptionId, long snapshotId, long scheduleId) {
    deletePendingPlanChange(subscriptionId);
    var row = new SubscriptionCommandRows.Pending();
    row.subscriptionId = subscriptionId;
    row.snapshotId = snapshotId;
    row.targetScheduleId = scheduleId;
    insert(row);
  }

public void setSubscriptionPet(long subscriptionId, long petId) {
    queries.update(subscription).set(subscription.petId, petId).where(subscription.id.eq(subscriptionId)).execute();
  }

public void markSkipped(long scheduleId) {
    queries.update(schedule).set(schedule.status, "SKIPPED").where(schedule.id.eq(scheduleId)).execute();
  }

public long insertScheduledAndReturnId(long subscriptionId, LocalDate date) {
    var row = new SubscriptionCommandRows.Schedule();
    row.subscriptionId = subscriptionId;
    row.scheduledDate = date;
    row.status = "SCHEDULED";
    insert(row);
    return row.id;
  }

public void retargetPendingPlanChange(long subscriptionId, long scheduleId) {
    queries.update(pending).set(pending.targetScheduleId, scheduleId).where(pending.subscriptionId.eq(subscriptionId)).execute();
  }

public void setSubscriptionStatus(long subscriptionId, String status) {
    queries.update(subscription).set(subscription.status, status).where(subscription.id.eq(subscriptionId)).execute();
  }

public void setScheduleStatus(long scheduleId, String status) {
    // Keep the original database comparison/collation, rather than a Java case-sensitive branch.
    var holdReason = new CaseBuilder().when(Expressions.asString(status).eq("HELD"))
        .then(schedule.holdReason).otherwise((String) null);
    queries.update(schedule).set(schedule.status, status).set(schedule.holdReason, holdReason)
        .where(schedule.id.eq(scheduleId)).execute();
  }

public void upsertScheduleAddon(long scheduleId, long skuId, int quantity, java.math.BigDecimal price) {
    nativeSql.update(
        "INSERT INTO"
            + " subscription_schedule_addons(schedule_id,sku_id,quantity,unit_price_krw,created_at,updated_at)"
            + " VALUES (?,?,?, ?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE"
            + " quantity=VALUES(quantity),unit_price_krw=VALUES(unit_price_krw),updated_at=UTC_TIMESTAMP(6)",
        scheduleId,
        skuId,
        quantity,
        price);
  }

public void deleteScheduleAddon(long scheduleId, long skuId) {
    if (queries.delete(addon).where(addon.scheduleId.eq(scheduleId), addon.skuId.eq(skuId)).execute() != 1)
      throw new SubscriptionApiException(404, "ADDON_NOT_FOUND", "Add-on을 찾을 수 없습니다.");
  }

public void deleteScheduleAddons(long subscriptionId) {
    nativeSql.update(
        "DELETE addon FROM subscription_schedule_addons addon JOIN subscription_schedules schedule"
            + " ON schedule.id=addon.schedule_id WHERE schedule.subscription_id=? AND"
            + " schedule.status IN ('SCHEDULED','HELD')",
        subscriptionId);
  }

public void moveScheduleAddons(long fromScheduleId, long toScheduleId) {
    queries.update(addon).set(addon.scheduleId, toScheduleId).where(addon.scheduleId.eq(fromScheduleId)).execute();
  }

public void reschedule(long scheduleId, LocalDate date) {
    queries.update(schedule).set(schedule.scheduledDate, date).where(schedule.id.eq(scheduleId)).execute();
  }

public void rescheduleHeld(long scheduleId, LocalDate date) {
    queries.update(schedule).set(schedule.scheduledDate, date).set(schedule.status, "SCHEDULED")
        .setNull(schedule.holdReason).where(schedule.id.eq(scheduleId)).execute();
  }

public void cancelUnorderedSchedules(long subscriptionId) {
    nativeSql.update(
        "UPDATE subscription_schedules schedule LEFT JOIN subscription_orders existing_order ON"
            + " existing_order.schedule_id=schedule.id SET schedule.status='CANCELED' WHERE"
            + " schedule.subscription_id=? AND schedule.status IN ('SCHEDULED','HELD') AND"
            + " existing_order.id IS NULL",
        subscriptionId);
  }

public void deletePendingPlanChange(long subscriptionId) {
    queries.delete(pending).where(pending.subscriptionId.eq(subscriptionId)).execute();
  }

public boolean incrementVersion(long subscriptionId, long expected) {
    return queries.update(subscription).set(subscription.version, subscription.version.add(1))
        .where(subscription.id.eq(subscriptionId), subscription.version.eq(expected)).execute() == 1;
  }

public void insertCommandHistory(long subscriptionId, String command, long before, long after) {
    nativeSql.update(
        "INSERT INTO"
            + " subscription_command_history(subscription_id,command_type,occurred_at,version_before,version_after)"
            + " VALUES (?,?,UTC_TIMESTAMP(6),?,?)",
        subscriptionId,
        command,
        before,
        after);
  }

public void deleteDeliveryReminder(long scheduleId) {
    var notification = new QNotificationEntity("notification");
    queries.delete(notification).where(notification.type.eq("SUBSCRIPTION_DELIVERY_REMINDER"),
        notification.referenceType.eq("SCHEDULE"), notification.referenceId.eq(scheduleId)).execute();
  }

public void deleteDeliveryReminders(long subscriptionId) {
    nativeSql.update(
        "DELETE notification FROM notifications notification JOIN subscription_schedules schedule"
            + " ON schedule.id=notification.reference_id AND notification.reference_type='SCHEDULE'"
            + " WHERE notification.type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " schedule.subscription_id=?",
        subscriptionId);
  }

private long snapshot(long subscriptionId, long versionId, int cycle, long price) {
    var row = new SubscriptionCommandRows.Snapshot();
    row.subscriptionId = subscriptionId;
    row.planVersionId = versionId;
    row.packagePriceKrw = price;
    row.deliveryCycleWeeks = cycle;
    insert(row);
    // Atomic INSERT SELECT avoids hydrating/copying every immutable plan item.
    nativeSql.update("INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) SELECT ?,sku_id,quantity FROM plan_items WHERE plan_version_id=?", row.id, versionId);
    return row.id;
  }

private long firstSku(long versionId) {
    var item = new QSubscriptionReadRows_PlanItem("item");
    Long id = queries.select(item.skuId).from(item).where(item.planVersionId.eq(versionId)).orderBy(item.skuId.asc()).fetchFirst();
    if (id == null) throw new org.springframework.dao.EmptyResultDataAccessException(1);
    return id;
  }
  private void insert(Object row) {
    entities.persist(row);
    entities.flush();
    entities.detach(row);
  }
}
