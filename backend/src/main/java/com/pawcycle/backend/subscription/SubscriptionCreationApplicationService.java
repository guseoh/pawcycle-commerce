package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.persistence.SubscriptionAggregatePersistence;

import com.pawcycle.backend.subscription.api.CreateSubscriptionRequest;
import com.pawcycle.backend.subscription.persistence.SubscriptionIdempotencyReservationPersistence;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SubscriptionCreationApplicationService {
  private final SubscriptionAggregatePersistence store;
  private final SubscriptionIdempotencyReservationPersistence reservations;
  private final SubscriptionApplicationSupport support;
  private final SubscriptionQueryApplicationService queries;

  SubscriptionCreationApplicationService(
      SubscriptionAggregatePersistence store,
      SubscriptionIdempotencyReservationPersistence reservations,
      SubscriptionQueryApplicationService queries,
      tools.jackson.databind.ObjectMapper json,
      java.time.Clock clock) {
    this.store = store;
    this.reservations = reservations;
    this.queries = queries;
    this.support = new SubscriptionApplicationSupport(json, clock);
  }

  @Transactional
  SubscriptionOperationResult create(
      long memberId, String key, CreateSubscriptionRequest request) {
    support.validateKey(key);
    String fingerprint = support.fingerprint(request);
    if (!reservations.reserveCreation(memberId, key, fingerprint)) {
      StoredIdempotencyResult stored = reservations.lockCreationResult(memberId, key);
      return replay(memberId, key, stored, fingerprint);
    }

    long petId = support.requiredLong(request.petId(), "petId");
    if (petId <= 0) throw support.validation("petId");
    long versionId = support.requiredLong(request.planVersionId(), "planVersionId");
    if (versionId <= 0) throw support.validation("planVersionId");
    int cycle = support.requiredInt(request.deliveryCycleWeeks(), "deliveryCycleWeeks");
    PetProjection pet = store.findOwnedPet(memberId, petId);
    PlanVersionProjection version = availableVersion(pet, versionId, cycle);
    LocalDate created = support.today();
    LocalDate next = created.plusWeeks(cycle);
    long subscriptionId =
        store.insertSubscription(memberId, versionId, cycle, petId, created, next);
    long snapshotId =
        store.createSnapshot(subscriptionId, versionId, cycle, version.packagePriceKrw());
    store.setCurrentSnapshot(subscriptionId, snapshotId);
    store.insertScheduled(subscriptionId, next);
    var response = queries.detail(memberId, subscriptionId, 0, 20, 0, 20);
    SubscriptionOperationResult result =
        new SubscriptionOperationResult(
            201, response, "/api/subscriptions/" + subscriptionId, "\"0\"", false);
    reservations.updateCreationResponse(memberId, key, subscriptionId, result, support.bodyJson(response));
    return result;
  }

  private SubscriptionOperationResult replay(
      long memberId,
      String key,
      StoredIdempotencyResult stored,
      String fingerprint) {
    if (!fingerprint.equals(stored.fingerprint()))
      throw new SubscriptionApiException(409, "IDEMPOTENCY_KEY_REUSED", "동일 key에 다른 요청 본문을 사용할 수 없습니다.");
    var body = support.responseBody(stored.bodyJson());
    reservations.updateStoredCreationBody(memberId, key, support.bodyJson(body));
    return new SubscriptionOperationResult(
        stored.status(), body, stored.location(), stored.etag(), true);
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
