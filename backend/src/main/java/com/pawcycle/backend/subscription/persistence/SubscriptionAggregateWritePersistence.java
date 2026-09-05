package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.SubscriptionApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Write-side SQL for aggregate state transitions and immutable snapshots. */
class SubscriptionAggregateWritePersistence {
  private final JdbcTemplate jdbc;

  SubscriptionAggregateWritePersistence(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

public long insertSubscription(
      long memberId, long versionId, int cycle, long petId, LocalDate created, LocalDate next) {
    jdbc.update(
        "INSERT INTO"
            + " subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,pet_id,status,version,current_snapshot_id,legacy_api_visible,runtime_managed)"
            + " VALUES (?,?,?,?,?,?,?,'ACTIVE',0,NULL,false,true)",
        memberId,
        firstSku(versionId),
        1,
        cycle,
        created,
        next,
        petId);
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

public void setCurrentSnapshot(long subscriptionId, long snapshotId) {
    jdbc.update(
        "UPDATE subscriptions SET current_snapshot_id=? WHERE id=?", snapshotId, subscriptionId);
  }

public void insertScheduled(long subscriptionId, LocalDate date) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id)"
            + " VALUES (?,?,'SCHEDULED',NULL)",
        subscriptionId,
        date);
  }

public long createSnapshot(long subscriptionId, long versionId, int cycle, long price) {
    return snapshot(subscriptionId, versionId, cycle, price);
  }

public long insertPet(long memberId, String name, String petType) {
    jdbc.update(
        "INSERT INTO pets(member_id,name,pet_type) VALUES (?,?,?)", memberId, name, petType);
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
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
    List<String> columns = new ArrayList<>();
    List<Object> arguments = new ArrayList<>();
    if (namePresent) {
      columns.add("name=?");
      arguments.add(name);
    }
    if (breedPresent) {
      columns.add("breed=?");
      arguments.add(breed);
    }
    if (weightPresent) {
      columns.add("weight_kg=?");
      arguments.add(weightKg);
    }
    arguments.add(petId);
    arguments.add(memberId);
    if (jdbc.update(
            "UPDATE pets SET " + String.join(",", columns) + " WHERE id=? AND member_id=?",
            arguments.toArray())
        != 1) throw new SubscriptionApiException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다.");
  }

public void replacePendingPlanChange(long subscriptionId, long snapshotId, long scheduleId) {
    jdbc.update("DELETE FROM pending_plan_changes WHERE subscription_id=?", subscriptionId);
    jdbc.update(
        "INSERT INTO pending_plan_changes(subscription_id,snapshot_id,target_schedule_id) VALUES"
            + " (?,?,?)",
        subscriptionId,
        snapshotId,
        scheduleId);
  }

public void setSubscriptionPet(long subscriptionId, long petId) {
    jdbc.update("UPDATE subscriptions SET pet_id=? WHERE id=?", petId, subscriptionId);
  }

public void markSkipped(long scheduleId) {
    jdbc.update("UPDATE subscription_schedules SET status='SKIPPED' WHERE id=?", scheduleId);
  }

public long insertScheduledAndReturnId(long subscriptionId, LocalDate date) {
    insertScheduled(subscriptionId, date);
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

public void retargetPendingPlanChange(long subscriptionId, long scheduleId) {
    jdbc.update(
        "UPDATE pending_plan_changes SET target_schedule_id=? WHERE subscription_id=?",
        scheduleId,
        subscriptionId);
  }

public void setSubscriptionStatus(long subscriptionId, String status) {
    jdbc.update("UPDATE subscriptions SET status=? WHERE id=?", status, subscriptionId);
  }

public void setScheduleStatus(long scheduleId, String status) {
    jdbc.update(
        "UPDATE subscription_schedules SET status=?,hold_reason=CASE WHEN ?='HELD' THEN hold_reason"
            + " ELSE NULL END WHERE id=?",
        status,
        status,
        scheduleId);
  }

public void upsertScheduleAddon(long scheduleId, long skuId, int quantity, java.math.BigDecimal price) {
    jdbc.update(
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
    if (jdbc.update(
            "DELETE FROM subscription_schedule_addons WHERE schedule_id=? AND sku_id=?",
            scheduleId,
            skuId)
        != 1) throw new SubscriptionApiException(404, "ADDON_NOT_FOUND", "Add-on을 찾을 수 없습니다.");
  }

public void deleteScheduleAddons(long subscriptionId) {
    jdbc.update(
        "DELETE addon FROM subscription_schedule_addons addon JOIN subscription_schedules schedule"
            + " ON schedule.id=addon.schedule_id WHERE schedule.subscription_id=? AND"
            + " schedule.status IN ('SCHEDULED','HELD')",
        subscriptionId);
  }

public void moveScheduleAddons(long fromScheduleId, long toScheduleId) {
    jdbc.update(
        "UPDATE subscription_schedule_addons SET schedule_id=? WHERE schedule_id=?",
        toScheduleId,
        fromScheduleId);
  }

public void reschedule(long scheduleId, LocalDate date) {
    jdbc.update("UPDATE subscription_schedules SET scheduled_date=? WHERE id=?", date, scheduleId);
  }

public void rescheduleHeld(long scheduleId, LocalDate date) {
    jdbc.update(
        "UPDATE subscription_schedules SET scheduled_date=?,status='SCHEDULED',hold_reason=NULL"
            + " WHERE id=?",
        date,
        scheduleId);
  }

public void cancelUnorderedSchedules(long subscriptionId) {
    jdbc.update(
        "UPDATE subscription_schedules schedule LEFT JOIN subscription_orders existing_order ON"
            + " existing_order.schedule_id=schedule.id SET schedule.status='CANCELED' WHERE"
            + " schedule.subscription_id=? AND schedule.status IN ('SCHEDULED','HELD') AND"
            + " existing_order.id IS NULL",
        subscriptionId);
  }

public void deletePendingPlanChange(long subscriptionId) {
    jdbc.update("DELETE FROM pending_plan_changes WHERE subscription_id=?", subscriptionId);
  }

public boolean incrementVersion(long subscriptionId, long expected) {
    return jdbc.update(
            "UPDATE subscriptions SET version=version+1 WHERE id=? AND version=?",
            subscriptionId,
            expected)
        == 1;
  }

public void insertCommandHistory(long subscriptionId, String command, long before, long after) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_command_history(subscription_id,command_type,occurred_at,version_before,version_after)"
            + " VALUES (?,?,UTC_TIMESTAMP(6),?,?)",
        subscriptionId,
        command,
        before,
        after);
  }

public void deleteDeliveryReminder(long scheduleId) {
    jdbc.update(
        "DELETE FROM notifications WHERE type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " reference_type='SCHEDULE' AND reference_id=?",
        scheduleId);
  }

public void deleteDeliveryReminders(long subscriptionId) {
    jdbc.update(
        "DELETE notification FROM notifications notification JOIN subscription_schedules schedule"
            + " ON schedule.id=notification.reference_id AND notification.reference_type='SCHEDULE'"
            + " WHERE notification.type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " schedule.subscription_id=?",
        subscriptionId);
  }

private long snapshot(long subscriptionId, long versionId, int cycle, long price) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks)"
            + " VALUES (?,?,?,?)",
        subscriptionId,
        versionId,
        price,
        cycle);
    long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) SELECT"
            + " ?,sku_id,quantity FROM plan_items WHERE plan_version_id=?",
        id,
        versionId);
    return id;
  }

private long firstSku(long versionId) {
    return jdbc.queryForObject(
        "SELECT sku_id FROM plan_items WHERE plan_version_id=? ORDER BY sku_id LIMIT 1",
        Long.class,
        versionId);
  }
}
