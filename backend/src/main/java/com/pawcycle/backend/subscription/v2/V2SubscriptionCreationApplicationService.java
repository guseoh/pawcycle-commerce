package com.pawcycle.backend.subscription.v2;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class V2SubscriptionCreationApplicationService {
	private final V2SubscriptionJdbcStore store;

	V2SubscriptionCreationApplicationService(V2SubscriptionJdbcStore store) { this.store = store; }

	@Transactional
	V2SubscriptionOperationResult create(long memberId, String key, Map<String, Object> body) {
		return store.createSubscription(memberId, key, body);
	}
}
