package com.pawcycle.backend.subscription.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Fully materialized subscription query output; no HTTP, persistence or validation behavior. */
public final class SubscriptionReadModel {
  private SubscriptionReadModel() {}

  public record History(String commandType, String result, String occurredAt) {}

  public record NextDelivery(
      long scheduleId,
      LocalDate scheduledDate,
      String status,
      long planVersionId,
      long packagePriceKrw,
      int deliveryCycleWeeks,
      List<ItemDetail> items,
      List<Addon> addOns,
      BigDecimal addOnTotalKrw,
      BigDecimal orderTotalKrw) {}

  public record Page<T>(int page, int size, long totalElements, List<T> items) {}

  public record PendingChange(
      long targetScheduleId,
      LocalDate appliesOn,
      long planVersionId,
      long packagePriceKrw,
      int deliveryCycleWeeks,
      List<ItemDetail> items) {}

  public record Pet(
      long petId,
      String name,
      String petType,
      String breed,
      BigDecimal weightKg,
      boolean profileComplete) {}

  public record Schedule(
      long scheduleId, LocalDate scheduledDate, String status, Long effectiveSnapshotId) {}

  public record Addon(
      long skuId,
      long productId,
      String productName,
      String skuName,
      int quantity,
      BigDecimal unitPriceKrw,
      BigDecimal lineAmountKrw) {}

  public record Detail(
      long subscriptionId,
      String status,
      long version,
      Pet pet,
      Snapshot currentSnapshot,
      LocalDate nextScheduledDate,
      Snapshot pendingSnapshot,
      NextDelivery nextDelivery,
      PendingChange pendingChange,
      Issue issue,
      List<String> availableActions,
      Page<Schedule> schedules,
      Page<History> commandHistory) {}

  public record Issue(String code, String message) {}

  public record ItemDetail(
      long skuId,
      String skuName,
      long productId,
      String productName,
      String thumbnailUrl,
      int quantity) {}

  public record Item(long skuId, int quantity) {}

  public record Snapshot(
      long planVersionId,
      long packagePriceKrw,
      int deliveryCycleWeeks,
      List<Item> items) {}

  public record Summary(
      long subscriptionId,
      String status,
      long version,
      Pet pet,
      Snapshot currentSnapshot,
      LocalDate nextScheduledDate) {}

}
