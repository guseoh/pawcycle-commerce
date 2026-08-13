package com.pawcycle.backend.subscription.v2;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class V2SubscriptionQueryApplicationService {
	private final V2SubscriptionJdbcStore store;

	V2SubscriptionQueryApplicationService(V2SubscriptionJdbcStore store) { this.store = store; }

	@Transactional(readOnly = true)
	Map<String, Object> subscriptions(long memberId, int page, int size) { return store.subscriptions(memberId, page, size); }

	@Transactional(readOnly = true)
	V2SubscriptionOperationResult subscription(long memberId, long subscriptionId, int schedulePage, int scheduleSize, int commandPage, int commandSize) {
		return store.subscription(memberId, subscriptionId, schedulePage, scheduleSize, commandPage, commandSize);
	}
}
