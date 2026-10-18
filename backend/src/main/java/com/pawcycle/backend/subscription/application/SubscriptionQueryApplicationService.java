package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.subscription.domain.SubscriptionFailure;
import com.pawcycle.backend.subscription.persistence.projection.CommandHistoryProjection;
import com.pawcycle.backend.subscription.persistence.projection.NextDeliveryProjection;
import com.pawcycle.backend.subscription.persistence.projection.PageProjection;
import com.pawcycle.backend.subscription.persistence.projection.PendingSubscriptionChange;
import com.pawcycle.backend.subscription.persistence.projection.PetProjection;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleAddonProjection;
import com.pawcycle.backend.subscription.persistence.projection.ScheduleViewProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionItemDetailProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionItemProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionProjection;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionSnapshot;
import com.pawcycle.backend.subscription.persistence.projection.SubscriptionSnapshotBase;

import com.pawcycle.backend.subscription.persistence.SubscriptionAggregatePersistence;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel.History;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.NextDelivery;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.Page;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.PendingChange;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.Pet;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.Schedule;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.Addon;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.Detail;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.Issue;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.ItemDetail;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.Item;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.Snapshot;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel.Summary;
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
  private final SubscriptionAggregatePersistence store;
  private final SubscriptionApplicationSupport support;

  SubscriptionQueryApplicationService(
      SubscriptionAggregatePersistence store,
      tools.jackson.databind.ObjectMapper json,
      java.time.Clock clock) {
    this.store = store;
    this.support = new SubscriptionApplicationSupport(json, clock);
  }

  @Transactional(readOnly = true)
  Page<Summary> subscriptions(long memberId, int page, int size) {
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
                        subscription.petId() == null ? null : pets.get(subscription.petId()),
                        snapshot(
                            snapshots.get(subscription.currentSnapshotId()),
                            items.getOrDefault(subscription.currentSnapshotId(), List.of())),
                        nextSchedules.get(subscription.id())))
            .toList());
  }

  @Transactional(readOnly = true)
  Detail subscription(
      long memberId,
      long subscriptionId,
      int schedulePage,
      int scheduleSize,
      int commandPage,
      int commandSize) {
    page(schedulePage, scheduleSize);
    page(commandPage, commandSize);
    Detail body =
        detail(memberId, subscriptionId, schedulePage, scheduleSize, commandPage, commandSize);
    return body;
  }

  Detail detail(
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
    Summary summary =
        summary(
            subscription,
            pet,
            snapshot(current),
            store.findNextSchedule(subscriptionId, support.today()).orElse(null));
    return new Detail(
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

  private Summary summary(
      SubscriptionProjection subscription,
      PetProjection pet,
      Snapshot currentSnapshot,
      LocalDate nextScheduledDate) {
    if (subscription.petId() != null && pet == null)
      throw new SubscriptionFailure(SubscriptionFailure.Kind.NOT_FOUND, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다.");
    return new Summary(
        subscription.id(),
        subscription.status(),
        subscription.version(),
        pet == null ? null : pet(pet),
        currentSnapshot,
        "ACTIVE".equals(subscription.status()) ? nextScheduledDate : null);
  }

  private Pet pet(PetProjection value) {
    return new Pet(
        value.id(),
        value.name(),
        value.petType(),
        value.breed(),
        value.weightKg(),
        value.profileComplete());
  }

  private Snapshot snapshot(
      SubscriptionSnapshotBase value, List<SubscriptionItemProjection> items) {
    if (value == null) throw new IllegalStateException("Subscription snapshot을 찾을 수 없습니다.");
    return new Snapshot(
        value.planVersionId(),
        value.packagePriceKrw(),
        value.deliveryCycleWeeks(),
        items.stream()
            .map(item -> new Item(item.skuId(), item.quantity()))
            .toList());
  }

  private Snapshot snapshot(SubscriptionSnapshot value) {
    return snapshot(
        new SubscriptionSnapshotBase(
            value.id(), value.planVersionId(), value.packagePriceKrw(), value.deliveryCycleWeeks()),
        value.items());
  }

  private NextDelivery nextDelivery(
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
    return new NextDelivery(
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

  private Addon addon(ScheduleAddonProjection addOn) {
    return new Addon(
        addOn.skuId(),
        addOn.productId(),
        addOn.productName(),
        addOn.skuName(),
        addOn.quantity(),
        addOn.unitPriceKrw(),
        addOn.lineAmount());
  }

  private PendingChange pendingChange(
      PendingSubscriptionChange change, SubscriptionSnapshot pending) {
    return new PendingChange(
        change.targetScheduleId(),
        change.targetScheduledDate(),
        pending.planVersionId(),
        pending.packagePriceKrw(),
        pending.deliveryCycleWeeks(),
        store.findSnapshotItemDetails(pending.id()).stream().map(this::itemDetail).toList());
  }

  private ItemDetail itemDetail(SubscriptionItemDetailProjection item) {
    return new ItemDetail(
        item.skuId(),
        item.skuName(),
        item.productId(),
        item.productName(),
        item.thumbnailUrl(),
        item.quantity());
  }

  private Issue issue(String holdReason) {
    if (holdReason == null) return null;
    return switch (holdReason) {
      case "MISSING_SHIPPING_ADDRESS" ->
          new Issue("SHIPPING_ADDRESS_REQUIRED", "배송지를 등록해 주세요.");
      case "MISSING_BILLING_METHOD" ->
          new Issue("BILLING_METHOD_REQUIRED", "결제 수단을 등록해 주세요.");
      case "PAYMENT_RETRY_EXHAUSTED" ->
          new Issue(
              "PAYMENT_SUPPORT_REQUIRED", "결제를 완료하지 못했습니다. 고객 지원에 문의해 주세요.");
      case "PAYMENT_RETRY_STOCK_UNAVAILABLE", "ORDER_STOCK_UNAVAILABLE" ->
          new Issue(
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

  private Schedule schedule(ScheduleViewProjection value) {
    return new Schedule(
        value.id(), value.scheduledDate(), value.status(), value.effectiveSnapshotId());
  }

  private History history(CommandHistoryProjection value) {
    return new History(value.commandType(), "SUCCEEDED", value.occurredAt());
  }

  private <T> Page<T> page(PageProjection<?> value, List<T> items) {
    return new Page<>(value.page(), value.size(), value.total(), items);
  }

  private int page(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size)
      throw support.validation("page");
    return page;
  }
}
