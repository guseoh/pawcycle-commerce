package com.pawcycle.backend.subscription.v2;

import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade for V2 callers. Use-case services own transaction boundaries; this class
 * contains no SQL, persistence mapping, or HTTP response construction.
 */
@Service
public class V2SubscriptionService {
	private final V2PetPlanApplicationService petPlans;
	private final V2SubscriptionCreationApplicationService creations;
	private final V2SubscriptionQueryApplicationService queries;
	private final V2SubscriptionCommandApplicationService commands;
	private final V2SubscriptionReconciliationApplicationService reconciliation;

	public V2SubscriptionService(
			V2PetPlanApplicationService petPlans,
			V2SubscriptionCreationApplicationService creations,
			V2SubscriptionQueryApplicationService queries,
			V2SubscriptionCommandApplicationService commands,
			V2SubscriptionReconciliationApplicationService reconciliation) {
		this.petPlans = petPlans;
		this.creations = creations;
		this.queries = queries;
		this.commands = commands;
		this.reconciliation = reconciliation;
	}

	public Map<String, Object> createPet(long memberId, Map<String, Object> body) { return petPlans.createPet(memberId, body); }
	public Map<String, Object> pets(long memberId, int page, int size) { return petPlans.pets(memberId, page, size); }
	public Map<String, Object> pet(long memberId, long petId) { return petPlans.pet(memberId, petId); }
	public Map<String, Object> plans(long memberId, long petId, int page, int size) { return petPlans.plans(memberId, petId, page, size); }
	public Map<String, Object> planVersion(long memberId, long petId, long versionId) { return petPlans.planVersion(memberId, petId, versionId); }

	public V2Result createSubscription(long memberId, String key, Map<String, Object> body) { return result(creations.create(memberId, key, body)); }
	public Map<String, Object> subscriptions(long memberId, int page, int size) { return queries.subscriptions(memberId, page, size); }
	public V2Result subscription(long memberId, long subscriptionId, int schedulePage, int scheduleSize, int commandPage, int commandSize) {
		return result(queries.subscription(memberId, subscriptionId, schedulePage, scheduleSize, commandPage, commandSize));
	}
	public V2Result command(long memberId, long subscriptionId, String command, String key, String ifMatch, Map<String, Object> body) {
		return result(commands.command(memberId, subscriptionId, command, key, ifMatch, body));
	}
	public void reconcileActiveSubscriptions() { reconciliation.reconcileActiveSubscriptions(); }

	private V2Result result(V2SubscriptionOperationResult result) {
		return new V2Result(result.status(), result.body(), result.location(), result.etag(), result.replay());
	}

	public record V2Result(int status, Map<String, Object> body, String location, String etag, boolean replay) {}
}
