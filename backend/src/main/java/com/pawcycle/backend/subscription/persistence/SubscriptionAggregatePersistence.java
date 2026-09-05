package com.pawcycle.backend.subscription.persistence;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aggregate persistence boundary; query and write SQL live in focused delegates. */
@Repository
public class SubscriptionAggregatePersistence extends SubscriptionAggregateQueryPersistence {
  private final SubscriptionAggregateWritePersistence writes;

  public SubscriptionAggregatePersistence(JdbcTemplate jdbc) {
    super(jdbc);
    this.writes = new SubscriptionAggregateWritePersistence(jdbc);
  }

  public long insertSubscription(long memberId, long versionId, int cycle, long petId, LocalDate created, LocalDate next) {
    return writes.insertSubscription(memberId, versionId, cycle, petId, created, next);
  }

  public void setCurrentSnapshot(long subscriptionId, long snapshotId) {
    writes.setCurrentSnapshot(subscriptionId, snapshotId);
  }

  public void insertScheduled(long subscriptionId, LocalDate date) {
    writes.insertScheduled(subscriptionId, date);
  }

  public long createSnapshot(long subscriptionId, long versionId, int cycle, long price) {
    return writes.createSnapshot(subscriptionId, versionId, cycle, price);
  }

  public long insertPet(long memberId, String name, String petType) {
    return writes.insertPet(memberId, name, petType);
  }

  public void updatePet(long memberId, long petId, String name, boolean namePresent, String breed, boolean breedPresent, java.math.BigDecimal weightKg, boolean weightPresent) {
    writes.updatePet(memberId, petId, name, namePresent, breed, breedPresent, weightKg, weightPresent);
  }

  public void replacePendingPlanChange(long subscriptionId, long snapshotId, long scheduleId) {
    writes.replacePendingPlanChange(subscriptionId, snapshotId, scheduleId);
  }

  public void setSubscriptionPet(long subscriptionId, long petId) {
    writes.setSubscriptionPet(subscriptionId, petId);
  }

  public void markSkipped(long scheduleId) {
    writes.markSkipped(scheduleId);
  }

  public long insertScheduledAndReturnId(long subscriptionId, LocalDate date) {
    return writes.insertScheduledAndReturnId(subscriptionId, date);
  }

  public void retargetPendingPlanChange(long subscriptionId, long scheduleId) {
    writes.retargetPendingPlanChange(subscriptionId, scheduleId);
  }

  public void setSubscriptionStatus(long subscriptionId, String status) {
    writes.setSubscriptionStatus(subscriptionId, status);
  }

  public void setScheduleStatus(long scheduleId, String status) {
    writes.setScheduleStatus(scheduleId, status);
  }

  public void upsertScheduleAddon(long scheduleId, long skuId, int quantity, java.math.BigDecimal price) {
    writes.upsertScheduleAddon(scheduleId, skuId, quantity, price);
  }

  public void deleteScheduleAddon(long scheduleId, long skuId) {
    writes.deleteScheduleAddon(scheduleId, skuId);
  }

  public void deleteScheduleAddons(long subscriptionId) {
    writes.deleteScheduleAddons(subscriptionId);
  }

  public void moveScheduleAddons(long fromScheduleId, long toScheduleId) {
    writes.moveScheduleAddons(fromScheduleId, toScheduleId);
  }

  public void reschedule(long scheduleId, LocalDate date) {
    writes.reschedule(scheduleId, date);
  }

  public void rescheduleHeld(long scheduleId, LocalDate date) {
    writes.rescheduleHeld(scheduleId, date);
  }

  public void cancelUnorderedSchedules(long subscriptionId) {
    writes.cancelUnorderedSchedules(subscriptionId);
  }

  public void deletePendingPlanChange(long subscriptionId) {
    writes.deletePendingPlanChange(subscriptionId);
  }

  public boolean incrementVersion(long subscriptionId, long expected) {
    return writes.incrementVersion(subscriptionId, expected);
  }

  public void insertCommandHistory(long subscriptionId, String command, long before, long after) {
    writes.insertCommandHistory(subscriptionId, command, before, after);
  }

  public void deleteDeliveryReminder(long scheduleId) {
    writes.deleteDeliveryReminder(scheduleId);
  }

  public void deleteDeliveryReminders(long subscriptionId) {
    writes.deleteDeliveryReminders(subscriptionId);
  }
}
