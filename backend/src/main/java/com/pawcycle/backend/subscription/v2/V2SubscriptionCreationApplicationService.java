package com.pawcycle.backend.subscription.v2;

import java.util.Map;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class V2SubscriptionCreationApplicationService {
	private final V2SubscriptionJdbcStore store;
	private final V2SubscriptionApplicationSupport support;
	private final V2SubscriptionQueryApplicationService queries;

	V2SubscriptionCreationApplicationService(V2SubscriptionJdbcStore store, V2SubscriptionQueryApplicationService queries, tools.jackson.databind.ObjectMapper json, java.time.Clock clock) {
		this.store = store;
		this.queries = queries;
		this.support = new V2SubscriptionApplicationSupport(json, clock);
	}

	@Transactional
	V2SubscriptionOperationResult create(long memberId, String key, Map<String, Object> body) {
		support.validateKey(key);
		String fingerprint = support.fingerprint(body);
		if (!store.reserveCreation(memberId, key, fingerprint)) {
			V2SubscriptionData.StoredIdempotencyResult stored = store.lockCreationResult(memberId, key);
			return replay(memberId, key, stored, fingerprint);
		}

		long petId = support.requiredLong(body, "petId");
		long versionId = support.requiredLong(body, "planVersionId");
		int cycle = support.requiredInt(body, "deliveryCycleWeeks");
		V2SubscriptionData.Pet pet = store.findOwnedPet(memberId, petId);
		V2SubscriptionData.PlanVersion version = availableVersion(pet, versionId, cycle);
		LocalDate created = support.today();
		LocalDate next = created.plusWeeks(cycle);
		long subscriptionId = store.insertSubscription(memberId, versionId, cycle, petId, created, next);
		long snapshotId = store.createSnapshot(subscriptionId, versionId, cycle, version.packagePriceKrw());
		store.setCurrentSnapshot(subscriptionId, snapshotId);
		store.insertScheduled(subscriptionId, next);
		Map<String, Object> response = queries.detailBody(memberId, subscriptionId, 0, 20, 0, 20);
		V2SubscriptionOperationResult result = new V2SubscriptionOperationResult(201, response, "/api/v2/subscriptions/" + subscriptionId, "\"0\"", false);
		store.updateCreationResponse(memberId, key, subscriptionId, result, support.bodyJson(response));
		return result;
	}

	private V2SubscriptionOperationResult replay(long memberId, String key, V2SubscriptionData.StoredIdempotencyResult stored, String fingerprint) {
		if (!fingerprint.equals(stored.fingerprint())) throw new V2ApiException(409, "IDEMPOTENCY_KEY_REUSED", "동일 key에 다른 요청 본문을 사용할 수 없습니다.");
		Map<String, Object> body = support.responseBody(stored.bodyJson());
		if (support.removeInternalSnapshotIds(body)) store.updateStoredCreationBody(memberId, key, support.bodyJson(body));
		return new V2SubscriptionOperationResult(stored.status(), body, stored.location(), stored.etag(), true);
	}

	private V2SubscriptionData.PlanVersion availableVersion(V2SubscriptionData.Pet pet, long versionId, int cycle) {
		V2SubscriptionData.PlanVersion version = store.findPlanVersion(versionId);
		if (!pet.petType().equals(version.targetPetType())) throw new V2ApiException(409, "PLAN_PET_TYPE_MISMATCH", "Pet 종과 Plan이 호환되지 않습니다.");
		LocalDate today = support.today();
		if (version.currentPlanVersionId() == null || version.currentPlanVersionId() != version.id() || version.planName() == null || !version.onSale() || version.migrationOnly() || (version.saleStartsOn() != null && version.saleStartsOn().isAfter(today)) || (version.saleEndsOn() != null && version.saleEndsOn().isBefore(today))) throw new V2ApiException(409, "PLAN_NOT_AVAILABLE", "판매 가능한 PlanVersion이 아닙니다.");
		if (!store.deliveryCycleAllowed(versionId, cycle)) throw new V2ApiException(409, "DELIVERY_CYCLE_NOT_ALLOWED", "허용되지 않은 배송 주기입니다.");
		return version;
	}
}
