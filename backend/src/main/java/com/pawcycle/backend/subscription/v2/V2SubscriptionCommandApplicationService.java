package com.pawcycle.backend.subscription.v2;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class V2SubscriptionCommandApplicationService {
	private final V2SubscriptionJdbcStore store;

	V2SubscriptionCommandApplicationService(V2SubscriptionJdbcStore store) { this.store = store; }

	@Transactional
	V2SubscriptionOperationResult command(long memberId, long subscriptionId, String command, String key, String ifMatch, Map<String, Object> body) {
		return store.command(memberId, subscriptionId, command, key, ifMatch, body);
	}
}
