package com.pawcycle.backend.subscription.v2;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class V2PetPlanApplicationService {
	private final V2SubscriptionJdbcStore store;

	V2PetPlanApplicationService(V2SubscriptionJdbcStore store) { this.store = store; }

	@Transactional
	Map<String, Object> createPet(long memberId, Map<String, Object> body) { return store.createPet(memberId, body); }

	@Transactional(readOnly = true)
	Map<String, Object> pets(long memberId, int page, int size) { return store.pets(memberId, page, size); }

	@Transactional(readOnly = true)
	Map<String, Object> pet(long memberId, long petId) { return store.pet(memberId, petId); }

	@Transactional(readOnly = true)
	Map<String, Object> plans(long memberId, long petId, int page, int size) { return store.plans(memberId, petId, page, size); }

	@Transactional(readOnly = true)
	Map<String, Object> planVersion(long memberId, long petId, long versionId) { return store.planVersion(memberId, petId, versionId); }
}
