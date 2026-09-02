package com.pawcycle.backend.subscription.v2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

final class V2SubscriptionData {
  private V2SubscriptionData() {}

  record Pet(long id, String name, String petType, String breed, BigDecimal weightKg) {
    Pet(long id, String name, String petType) {
      this(id, name, petType, null, null);
    }

    boolean profileComplete() {
      return breed != null && weightKg != null;
    }
  }

  record PlanVersion(
      long planId,
      String planName,
      Long currentPlanVersionId,
      String targetPetType,
      boolean onSale,
      LocalDate saleStartsOn,
      LocalDate saleEndsOn,
      long id,
      long packagePriceKrw,
      boolean migrationOnly) {}

  record Subscription(
      long id,
      long memberId,
      String status,
      long version,
      Long petId,
      int deliveryCycleWeeks,
      long currentSnapshotId) {}

  record Schedule(long id, LocalDate scheduledDate) {}

  record ScheduleAddon(
      long scheduleId,
      long skuId,
      long productId,
      String productName,
      String skuName,
      int quantity,
      BigDecimal unitPriceKrw) {
    BigDecimal lineAmount() {
      return unitPriceKrw.multiply(BigDecimal.valueOf(quantity));
    }
  }

  record AddonSku(
      long skuId,
      long productId,
      String productName,
      String skuName,
      BigDecimal price,
      boolean eligible) {}

  record PendingChange(long snapshotId, long targetScheduleId, LocalDate targetScheduledDate) {}

  record NextDeliverySchedule(
      long id,
      LocalDate scheduledDate,
      String status,
      String holdReason,
      Long effectiveSnapshotId) {}

  record ProcessedSchedule(LocalDate scheduledDate, int deliveryCycleWeeks) {}

  record Snapshot(
      long id,
      long planVersionId,
      long packagePriceKrw,
      int deliveryCycleWeeks,
      List<Item> items) {}

  record SnapshotBase(long id, long planVersionId, long packagePriceKrw, int deliveryCycleWeeks) {}

  record Item(long skuId, int quantity) {}

  record ItemDetail(
      long skuId,
      String skuName,
      long productId,
      String productName,
      String thumbnailUrl,
      int quantity) {}

  record ScheduleView(long id, LocalDate scheduledDate, String status, Long effectiveSnapshotId) {}

  record CommandHistory(String commandType, String occurredAt) {}

  record Page<T>(int page, int size, long total, List<T> items) {}

  record StoredIdempotencyResult(
      String fingerprint, int status, String bodyJson, String location, String etag) {}
}
