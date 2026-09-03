package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.api.CommandHistoryResponse;
import com.pawcycle.backend.subscription.api.NextDeliveryResponse;
import com.pawcycle.backend.subscription.api.PageResponse;
import com.pawcycle.backend.subscription.api.PendingChangeResponse;
import com.pawcycle.backend.subscription.api.PetResponse;
import com.pawcycle.backend.subscription.api.ScheduleResponse;
import com.pawcycle.backend.subscription.api.SubscriptionAddonResponse;
import com.pawcycle.backend.subscription.api.SubscriptionDetailResponse;
import com.pawcycle.backend.subscription.api.SubscriptionIssueResponse;
import com.pawcycle.backend.subscription.api.SubscriptionItemDetailResponse;
import com.pawcycle.backend.subscription.api.SubscriptionItemResponse;
import com.pawcycle.backend.subscription.api.SubscriptionSnapshotResponse;
import com.pawcycle.backend.subscription.api.SubscriptionSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SubscriptionQueryApplicationService {
  private final SubscriptionPersistenceAdapter store;
  private final SubscriptionApplicationSupport support;

  SubscriptionQueryApplicationService(
      SubscriptionPersistenceAdapter store,
      tools.jackson.databind.ObjectMapper json,
      java.time.Clock clock) {
    this.store = store;
    this.support = new SubscriptionApplicationSupport(json, clock);
  }

  @Transactional(readOnly = true)
  PageResponse<SubscriptionSummaryResponse> subscriptions(long memberId, int page, int size) {
    PageProjection<SubscriptionProjection> subscriptions =
        store.findSubscriptions(memberId, page(page, size), size);
    List<Long> subscriptionIds =
        subscriptions.items().stream().map(SubscriptionProjection::id).toList();
    Map<Long, PetProjection> pets =
        store.findOwnedPets(
            memberId,
            subscriptions.items().stream()
                .map(SubscriptionProjection::petId)
                .filter(java.util.Objects::nonNull)
                .toList());
    Map<Long, SubscriptionSnapshotBase> snapshots =
        store.findSnapshots(
            subscriptions.items().stream()
                .map(SubscriptionProjection::currentSnapshotId)
                .toList());
    Map<Long, List<SubscriptionItemProjection>> items =
        store.findSnapshotItems(snapshots.keySet().stream().toList());
    Map<Long, LocalDate> nextSchedules =
        store.findNextSchedules(subscriptionIds, support.today());
    return page(
        subscriptions,
        subscriptions.items().stream()
            .map(
                subscription ->
                    summary(
                        subscription,
                        pets.get(subscription.petId()),
                        snapshot(
                            snapshots.get(subscription.currentSnapshotId()),
                            items.getOrDefault(subscription.currentSnapshotId(), List.of())),
                        nextSchedules.get(subscription.id())))
            .toList());
  }

  @Transactional(readOnly = true)
  SubscriptionOperationResult subscription(
      long memberId,
      long subscriptionId,
      int schedulePage,
      int scheduleSize,
      int commandPage,
      int commandSize) {
    page(schedulePage, scheduleSize);
    page(commandPage, commandSize);
    SubscriptionDetailResponse body =
        detail(memberId, subscriptionId, schedulePage, scheduleSize, commandPage, commandSize);
    return new SubscriptionOperationResult(
        200, body, null, "\"" + body.version() + "\"", false);
  }

  SubscriptionDetailResponse detail(
      long memberId,
      long subscriptionId,
      int schedulePage,
      int scheduleSize,
      int commandPage,
      int commandSize) {
    int checkedSchedulePage = page(schedulePage, scheduleSize);
    int checkedCommandPage = page(commandPage, commandSize);
    SubscriptionProjection subscription = store.findOwnedSubscription(memberId, subscriptionId);
    PetProjection pet =
        subscription.petId() == null ? null : store.findOwnedPet(memberId, subscription.petId());
    SubscriptionSnapshot current = store.findSnapshot(subscription.currentSnapshotId());
    Optional<PendingSubscriptionChange> pendingChange = store.findPendingChange(subscriptionId);
    SubscriptionSnapshot pending =
        pendingChange
            .map(PendingSubscriptionChange::snapshotId)
            .map(store::findSnapshot)
            .orElse(null);
    NextDeliveryProjection nextDelivery =
        "ACTIVE".equals(subscription.status())
            ? store.findNextDeliverySchedule(subscriptionId).orElse(null)
            : null;
    PageProjection<ScheduleViewProjection> schedules =
        store.findScheduleViews(subscriptionId, checkedSchedulePage, scheduleSize);
    PageProjection<CommandHistoryProjection> history =
        store.findCommandHistory(subscriptionId, checkedCommandPage, commandSize);
    SubscriptionSummaryResponse summary =
        summary(
            subscription,
            pet,
            snapshot(current),
            store.findNextSchedule(subscriptionId, support.today()).orElse(null));
    return new SubscriptionDetailResponse(
        summary.subscriptionId(),
        summary.status(),
        summary.version(),
        summary.pet(),
        summary.currentSnapshot(),
        summary.nextScheduledDate(),
        pending == null ? null : snapshot(pending),
        nextDelivery == null
            ? null
            : nextDelivery(nextDelivery, current, pendingChange.orElse(null), pending),
        pendingChange.map(change -> pendingChange(change, pending)).orElse(null),
        nextDelivery == null ? null : issue(nextDelivery.holdReason()),
        availableActions(subscription, nextDelivery),
        page(schedules, schedules.items().stream().map(this::schedule).toList()),
        page(history, history.items().stream().map(this::history).toList()));
  }

  private SubscriptionSummaryResponse summary(
      SubscriptionProjection subscription,
      PetProjection pet,
      SubscriptionSnapshotResponse currentSnapshot,
      LocalDate nextScheduledDate) {
    if (subscription.petId() != null && pet == null)
      throw new SubscriptionApiException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다.");
    return new SubscriptionSummaryResponse(
        subscription.id(),
        subscription.status(),
        subscription.version(),
        pet == null ? null : pet(pet),
        currentSnapshot,
        "ACTIVE".equals(subscription.status()) ? nextScheduledDate : null);
  }

  private PetResponse pet(PetProjection value) {
    return new PetResponse(
        value.id(),
        value.name(),
        value.petType(),
        value.breed(),
        value.weightKg(),
        value.profileComplete());
  }

  private SubscriptionSnapshotResponse snapshot(
      SubscriptionSnapshotBase value, List<SubscriptionItemProjection> items) {
    if (value == null) throw new IllegalStateException("Subscription snapshot을 찾을 수 없습니다.");
    return new SubscriptionSnapshotResponse(
        value.planVersionId(),
        value.packagePriceKrw(),
        value.deliveryCycleWeeks(),
        items.stream()
            .map(item -> new SubscriptionItemResponse(item.skuId(), item.quantity()))
            .toList());
  }

  private SubscriptionSnapshotResponse snapshot(SubscriptionSnapshot value) {
    return snapshot(
        new SubscriptionSnapshotBase(
            value.id(), value.planVersionId(), value.packagePriceKrw(), value.deliveryCycleWeeks()),
        value.items());
  }

  private NextDeliveryResponse nextDelivery(
      NextDeliveryProjection schedule,
      SubscriptionSnapshot current,
      PendingSubscriptionChange pendingChange,
      SubscriptionSnapshot pending) {
    long snapshotId =
        schedule.effectiveSnapshotId() != null
            ? schedule.effectiveSnapshotId()
            : pendingChange != null && pendingChange.targetScheduleId() == schedule.id()
                ? pendingChange.snapshotId()
                : current.id();
    SubscriptionSnapshot effective =
        snapshotId == current.id()
            ? current
            : pending != null && snapshotId == pending.id()
                ? pending
                : store.findSnapshot(snapshotId);
    List<ScheduleAddonProjection> addOns = store.findScheduleAddons(schedule.id());
    BigDecimal addOnTotal =
        addOns.stream()
            .map(ScheduleAddonProjection::lineAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new NextDeliveryResponse(
        schedule.id(),
        schedule.scheduledDate(),
        schedule.status(),
        effective.planVersionId(),
        effective.packagePriceKrw(),
        effective.deliveryCycleWeeks(),
        store.findSnapshotItemDetails(effective.id()).stream().map(this::itemDetail).toList(),
        addOns.stream().map(this::addon).toList(),
        addOnTotal,
        BigDecimal.valueOf(effective.packagePriceKrw()).add(addOnTotal));
  }

  private SubscriptionAddonResponse addon(ScheduleAddonProjection addOn) {
    return new SubscriptionAddonResponse(
        addOn.skuId(),
        addOn.productId(),
        addOn.productName(),
        addOn.skuName(),
        addOn.quantity(),
        addOn.unitPriceKrw(),
        addOn.lineAmount());
  }

  private PendingChangeResponse pendingChange(
      PendingSubscriptionChange change, SubscriptionSnapshot pending) {
    return new PendingChangeResponse(
        change.targetScheduleId(),
        change.targetScheduledDate(),
        pending.planVersionId(),
        pending.packagePriceKrw(),
        pending.deliveryCycleWeeks(),
        store.findSnapshotItemDetails(pending.id()).stream().map(this::itemDetail).toList());
  }

  private SubscriptionItemDetailResponse itemDetail(SubscriptionItemDetailProjection item) {
    return new SubscriptionItemDetailResponse(
        item.skuId(),
        item.skuName(),
        item.productId(),
        item.productName(),
        item.thumbnailUrl(),
        item.quantity());
  }

  private SubscriptionIssueResponse issue(String holdReason) {
    if (holdReason == null) return null;
    return switch (holdReason) {
      case "MISSING_SHIPPING_ADDRESS" ->
          new SubscriptionIssueResponse("SHIPPING_ADDRESS_REQUIRED", "배송지를 등록해 주세요.");
      case "MISSING_BILLING_METHOD" ->
          new SubscriptionIssueResponse("BILLING_METHOD_REQUIRED", "결제 수단을 등록해 주세요.");
      case "PAYMENT_RETRY_EXHAUSTED" ->
          new SubscriptionIssueResponse(
              "PAYMENT_SUPPORT_REQUIRED", "결제를 완료하지 못했습니다. 고객 지원에 문의해 주세요.");
      case "PAYMENT_RETRY_STOCK_UNAVAILABLE", "ORDER_STOCK_UNAVAILABLE" ->
          new SubscriptionIssueResponse(
              "STOCK_UNAVAILABLE", "재고를 확보하지 못해 배송이 보류되었습니다.");
      default -> throw new IllegalStateException("알 수 없는 Subscription issue입니다.");
    };
  }

  private List<String> availableActions(
      SubscriptionProjection subscription, NextDeliveryProjection nextDelivery) {
    if ("PAUSED".equals(subscription.status()))
      return List.of("RESUME", "CANCEL", "UPDATE_SHIPPING_ADDRESS");
    if (!"ACTIVE".equals(subscription.status())) return List.of();
    if (nextDelivery == null) return List.of("CANCEL");
    if ("HELD".equals(nextDelivery.status()))
      return switch (nextDelivery.holdReason()) {
        case "MISSING_SHIPPING_ADDRESS" -> List.of("UPDATE_SHIPPING_ADDRESS", "CANCEL");
        case "MISSING_BILLING_METHOD" -> List.of("REGISTER_BILLING_METHOD", "CANCEL");
        case "ORDER_STOCK_UNAVAILABLE" ->
            store.scheduleAddonCount(nextDelivery.id()) > 0
                ? List.of("REMOVE_NEXT_DELIVERY_ADDON", "CANCEL")
                : List.of("CANCEL");
        default -> List.of("CANCEL");
      };
    if (!"SCHEDULED".equals(nextDelivery.status())) return List.of("CANCEL");
    return scheduledActions(nextDelivery.id());
  }

  private List<String> scheduledActions(long scheduleId) {
    List<String> actions =
        new ArrayList<>(
            List.of(
                "CHANGE_PLAN",
                "CHANGE_DELIVERY_CYCLE",
                "RESCHEDULE_NEXT",
                "SKIP_NEXT",
                "PAUSE",
                "SET_NEXT_DELIVERY_ADDON"));
    if (store.scheduleAddonCount(scheduleId) > 0) actions.add("REMOVE_NEXT_DELIVERY_ADDON");
    actions.addAll(List.of("CANCEL", "UPDATE_SHIPPING_ADDRESS"));
    return actions;
  }

  private ScheduleResponse schedule(ScheduleViewProjection value) {
    return new ScheduleResponse(
        value.id(), value.scheduledDate(), value.status(), value.effectiveSnapshotId());
  }

  private CommandHistoryResponse history(CommandHistoryProjection value) {
    return new CommandHistoryResponse(value.commandType(), "SUCCEEDED", value.occurredAt());
  }

  private <T> PageResponse<T> page(PageProjection<?> value, List<T> items) {
    return new PageResponse<>(value.page(), value.size(), value.total(), items);
  }

  private int page(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size)
      throw support.validation("page");
    return page;
  }
}
