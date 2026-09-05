package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.persistence.SubscriptionAggregatePersistence;

import com.pawcycle.backend.subscription.api.SubscriptionCommandRequest;
import com.pawcycle.backend.subscription.persistence.SubscriptionIdempotencyReservationPersistence;
import com.pawcycle.backend.subscription.persistence.SubscriptionSchedulePersistence;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SubscriptionCommandApplicationService {
  private static final List<String> HELD_SCHEDULE_MUTATIONS =
      List.of(
          "CHANGE_PLAN",
          "CHANGE_DELIVERY_CYCLE",
          "RESCHEDULE_NEXT",
          "SKIP_NEXT",
          "PAUSE",
          "SET_NEXT_DELIVERY_ADDON");

  private final SubscriptionAggregatePersistence store;
  private final SubscriptionIdempotencyReservationPersistence reservations;
  private final SubscriptionSchedulePersistence schedules;
  private final SubscriptionApplicationSupport support;
  private final SubscriptionQueryApplicationService queries;

  SubscriptionCommandApplicationService(
      SubscriptionAggregatePersistence store,
      SubscriptionIdempotencyReservationPersistence reservations,
      SubscriptionSchedulePersistence schedules,
      SubscriptionQueryApplicationService queries,
      tools.jackson.databind.ObjectMapper json,
      java.time.Clock clock) {
    this.store = store;
    this.reservations = reservations;
    this.schedules = schedules;
    this.queries = queries;
    this.support = new SubscriptionApplicationSupport(json, clock);
  }

  @Transactional
  SubscriptionOperationResult command(
      long memberId,
      long subscriptionId,
      String command,
      String key,
      String ifMatch,
      SubscriptionCommandRequest request) {
    String normalized = command.toUpperCase(Locale.ROOT).replace('-', '_');
    if (!List.of(
            "CHANGE_PLAN",
            "CHANGE_DELIVERY_CYCLE",
            "RESCHEDULE_NEXT",
            "SKIP_NEXT",
            "PAUSE",
            "RESUME",
            "CANCEL",
            "SET_NEXT_DELIVERY_ADDON",
            "REMOVE_NEXT_DELIVERY_ADDON")
        .contains(normalized))
      throw new SubscriptionApiException(404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다.");
    support.validateKey(key);
    String fingerprint = support.fingerprint(request);
    SubscriptionProjection subscription =
        store.lockOwnedSubscription(memberId, subscriptionId);
    if (!reservations.reserveCommand(memberId, subscriptionId, normalized, key, fingerprint))
      return replay(
          memberId,
          subscriptionId,
          normalized,
          key,
          reservations.lockCommandResult(memberId, subscriptionId, normalized, key),
          fingerprint);

    long expected = support.parseEtag(ifMatch);
    if (subscription.version() != expected)
      throw new SubscriptionApiException(
          412, "SUBSCRIPTION_VERSION_MISMATCH", "Subscription version이 일치하지 않습니다.");
    rejectHeldScheduleMutation(subscription, normalized);
    switch (normalized) {
      case "CHANGE_PLAN" -> changePlan(memberId, subscription, request);
      case "CHANGE_DELIVERY_CYCLE" -> changeDeliveryCycle(subscription, request);
      case "RESCHEDULE_NEXT" -> rescheduleNext(subscription, request);
      case "SKIP_NEXT" -> skip(subscription);
      case "PAUSE" -> pause(subscription);
      case "RESUME" -> resume(subscription);
      case "CANCEL" -> cancel(subscription);
      case "SET_NEXT_DELIVERY_ADDON" -> setNextDeliveryAddon(subscription, request);
      case "REMOVE_NEXT_DELIVERY_ADDON" -> removeNextDeliveryAddon(subscription, request);
      default -> throw new IllegalStateException();
    }
    if (!store.incrementVersion(subscriptionId, expected))
      throw new SubscriptionApiException(
          412, "SUBSCRIPTION_VERSION_MISMATCH", "Subscription version이 일치하지 않습니다.");
    store.insertCommandHistory(subscriptionId, normalized, expected, expected + 1);
    var response = queries.detail(memberId, subscriptionId, 0, 20, 0, 20);
    SubscriptionOperationResult result =
        new SubscriptionOperationResult(200, response, null, "\"" + (expected + 1) + "\"", false);
    reservations.updateCommandResponse(
        memberId, subscriptionId, normalized, key, result, support.bodyJson(response));
    return result;
  }

  private void rejectHeldScheduleMutation(
      SubscriptionProjection subscription, String command) {
    if (!HELD_SCHEDULE_MUTATIONS.contains(command)) return;
    boolean held =
        store
            .findNextDeliverySchedule(subscription.id())
            .map(schedule -> "HELD".equals(schedule.status()))
            .orElse(false);
    if (held) throw support.state();
  }

  private SubscriptionOperationResult replay(
      long memberId,
      long subscriptionId,
      String command,
      String key,
      StoredIdempotencyResult stored,
      String fingerprint) {
    if (!fingerprint.equals(stored.fingerprint()))
      throw new SubscriptionApiException(409, "IDEMPOTENCY_KEY_REUSED", "동일 key에 다른 요청 본문을 사용할 수 없습니다.");
    var body = support.responseBody(stored.bodyJson());
    reservations.updateStoredCommandBody(
        memberId, subscriptionId, command, key, support.bodyJson(body));
    return new SubscriptionOperationResult(
        stored.status(), body, stored.location(), stored.etag(), true);
  }

  private void changePlan(
      long memberId, SubscriptionProjection subscription, SubscriptionCommandRequest request) {
    if (!"ACTIVE".equals(subscription.status())) throw support.state();
    long petId =
        subscription.petId() == null
            ? support.requiredLong(request.petId(), "petId")
            : subscription.petId();
    if (request.petId() != null && petId != request.petId())
      throw new SubscriptionApiException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다.");
    PetProjection pet = store.findOwnedPet(memberId, petId);
    SubscriptionSnapshot basis = pendingOrCurrentSnapshot(subscription);
    long versionId = support.requiredLong(request.planVersionId(), "planVersionId");
    PlanVersionProjection version =
        availableVersion(pet, versionId, basis.deliveryCycleWeeks());
    ScheduleProjection schedule = store.lockNextScheduled(subscription.id());
    if (store.scheduleAddonConflicts(schedule.id(), versionId))
      throw new SubscriptionApiException(
          409, "ADDON_CONFLICTS_WITH_PLAN", "Add-on을 먼저 제거해야 Plan을 변경할 수 있습니다.");
    long snapshot =
        store.createSnapshot(
            subscription.id(), versionId, basis.deliveryCycleWeeks(), version.packagePriceKrw());
    store.replacePendingPlanChange(subscription.id(), snapshot, schedule.id());
    if (subscription.petId() == null) store.setSubscriptionPet(subscription.id(), petId);
  }

  private void changeDeliveryCycle(
      SubscriptionProjection subscription, SubscriptionCommandRequest request) {
    if (!"ACTIVE".equals(subscription.status())) throw support.state();
    int cycle = support.requiredInt(request.deliveryCycleWeeks(), "deliveryCycleWeeks");
    if (!List.of(2, 4, 8).contains(cycle))
      throw new SubscriptionApiException(409, "DELIVERY_CYCLE_NOT_ALLOWED", "허용되지 않은 배송 주기입니다.");
    SubscriptionSnapshot basis = pendingOrCurrentSnapshot(subscription);
    if (!store.deliveryCycleAllowed(basis.planVersionId(), cycle))
      throw new SubscriptionApiException(
          409, "DELIVERY_CYCLE_NOT_ALLOWED", "적용될 PlanVersion이 요청 배송 주기를 지원하지 않습니다.");
    ScheduleProjection schedule = store.lockNextScheduled(subscription.id());
    long snapshot =
        store.createSnapshot(
            subscription.id(), basis.planVersionId(), cycle, basis.packagePriceKrw());
    store.replacePendingPlanChange(subscription.id(), snapshot, schedule.id());
  }

  private void rescheduleNext(
      SubscriptionProjection subscription, SubscriptionCommandRequest request) {
    if (!"ACTIVE".equals(subscription.status())) throw support.state();
    LocalDate requestedDate = support.requiredDate(request.scheduledDate(), "scheduledDate");
    if (!requestedDate.isAfter(support.today()))
      throw new SubscriptionApiException(409, "SCHEDULE_DATE_NOT_FUTURE", "새 배송일은 오늘보다 미래여야 합니다.");
    ScheduleProjection schedule = store.lockNextScheduled(subscription.id());
    if (requestedDate.equals(schedule.scheduledDate())
        || store.scheduleDateTaken(subscription.id(), requestedDate, schedule.id()))
      throw new SubscriptionApiException(409, "SCHEDULE_DATE_CONFLICT", "같은 날짜의 Schedule이 이미 존재합니다.");
    store.reschedule(schedule.id(), requestedDate);
    store.deleteDeliveryReminder(schedule.id());
  }

  private SubscriptionSnapshot pendingOrCurrentSnapshot(
      SubscriptionProjection subscription) {
    return store
        .findPendingChange(subscription.id())
        .map(PendingSubscriptionChange::snapshotId)
        .map(store::findSnapshot)
        .orElseGet(() -> store.findSnapshot(subscription.currentSnapshotId()));
  }

  private void skip(SubscriptionProjection subscription) {
    if (!"ACTIVE".equals(subscription.status())) throw support.state();
    ScheduleProjection schedule = store.lockNextScheduled(subscription.id());
    store.markSkipped(schedule.id());
    store.deleteDeliveryReminder(schedule.id());
    LocalDate date =
        SubscriptionOrderAutomationService.firstFutureDate(
            schedule.scheduledDate(), subscription.deliveryCycleWeeks(), support.today());
    long next = store.insertScheduledAndReturnId(subscription.id(), date);
    store.moveScheduleAddons(schedule.id(), next);
    store.retargetPendingPlanChange(subscription.id(), next);
  }

  private void pause(SubscriptionProjection subscription) {
    if (!"ACTIVE".equals(subscription.status())) throw support.state();
    ScheduleProjection schedule = store.lockNextScheduled(subscription.id());
    store.setSubscriptionStatus(subscription.id(), "PAUSED");
    store.setScheduleStatus(schedule.id(), "HELD");
    store.deleteDeliveryReminder(schedule.id());
  }

  private void resume(SubscriptionProjection subscription) {
    if (!"PAUSED".equals(subscription.status())) throw support.state();
    LocalDate candidate = support.today().plusWeeks(subscription.deliveryCycleWeeks());
    for (Long heldId : schedules.heldScheduleIds(subscription.id())) {
      while (store.scheduleDateTaken(subscription.id(), candidate, heldId))
        candidate = candidate.plusWeeks(subscription.deliveryCycleWeeks());
      store.rescheduleHeld(heldId, candidate);
      candidate = candidate.plusWeeks(subscription.deliveryCycleWeeks());
    }
    store.setSubscriptionStatus(subscription.id(), "ACTIVE");
  }

  private void cancel(SubscriptionProjection subscription) {
    if (!List.of("ACTIVE", "PAUSED").contains(subscription.status())) throw support.state();
    store.setSubscriptionStatus(subscription.id(), "CANCELED");
    store.cancelUnorderedSchedules(subscription.id());
    store.deletePendingPlanChange(subscription.id());
    store.deleteScheduleAddons(subscription.id());
    store.deleteDeliveryReminders(subscription.id());
  }

  private void setNextDeliveryAddon(
      SubscriptionProjection subscription, SubscriptionCommandRequest request) {
    if (!"ACTIVE".equals(subscription.status())) throw support.state();
    long skuId = support.requiredLong(request.skuId(), "skuId");
    int quantity = support.requiredInt(request.quantity(), "quantity");
    if (quantity < 1 || quantity > 10)
      throw new SubscriptionApiException(400, "VALIDATION_FAILED", "quantity는 1~10이어야 합니다.");
    ScheduleProjection schedule = store.lockNextScheduled(subscription.id());
    SubscriptionSnapshot basis = pendingOrCurrentSnapshot(subscription);
    if (store.planContainsSku(basis.planVersionId(), skuId))
      throw new SubscriptionApiException(
          409, "ADDON_SKU_ALREADY_INCLUDED", "기본 Plan에 포함된 SKU는 Add-on으로 추가할 수 없습니다.");
    if (!store.hasScheduleAddon(schedule.id(), skuId)
        && store.scheduleAddonCount(schedule.id()) >= 10)
      throw new SubscriptionApiException(409, "ADDON_LIMIT_EXCEEDED", "한 Schedule의 Add-on은 최대 10개입니다.");
    AddonSkuProjection sku = store.findEligibleAddonSku(skuId);
    if (!sku.eligible())
      throw new SubscriptionApiException(409, "ADDON_NOT_AVAILABLE", "Add-on을 구매할 수 없습니다.");
    store.upsertScheduleAddon(schedule.id(), skuId, quantity, sku.price());
  }

  private void removeNextDeliveryAddon(
      SubscriptionProjection subscription, SubscriptionCommandRequest request) {
    if (!"ACTIVE".equals(subscription.status())) throw support.state();
    long skuId = support.requiredLong(request.skuId(), "skuId");
    ScheduleProjection schedule = store.lockNextScheduled(subscription.id());
    store.deleteScheduleAddon(schedule.id(), skuId);
    store.deleteDeliveryReminder(schedule.id());
  }

  private PlanVersionProjection availableVersion(
      PetProjection pet, long versionId, int cycle) {
    PlanVersionProjection version = store.findPlanVersion(versionId);
    if (!pet.petType().equals(version.targetPetType()))
      throw new SubscriptionApiException(409, "PLAN_PET_TYPE_MISMATCH", "Pet 종과 Plan이 호환되지 않습니다.");
    LocalDate today = support.today();
    if (version.currentPlanVersionId() == null
        || version.currentPlanVersionId() != version.id()
        || version.planName() == null
        || !version.onSale()
        || version.migrationOnly()
        || (version.saleStartsOn() != null && version.saleStartsOn().isAfter(today))
        || (version.saleEndsOn() != null && version.saleEndsOn().isBefore(today)))
      throw new SubscriptionApiException(409, "PLAN_NOT_AVAILABLE", "판매 가능한 PlanVersion이 아닙니다.");
    if (!store.deliveryCycleAllowed(versionId, cycle))
      throw new SubscriptionApiException(409, "DELIVERY_CYCLE_NOT_ALLOWED", "허용되지 않은 배송 주기입니다.");
    return version;
  }
}
