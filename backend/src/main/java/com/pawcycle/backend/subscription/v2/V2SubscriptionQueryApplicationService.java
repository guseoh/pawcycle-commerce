package com.pawcycle.backend.subscription.v2;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class V2SubscriptionQueryApplicationService {
	private final V2SubscriptionJdbcStore store;
	private final V2SubscriptionApplicationSupport support;

	V2SubscriptionQueryApplicationService(V2SubscriptionJdbcStore store, tools.jackson.databind.ObjectMapper json, java.time.Clock clock) { this.store = store; this.support = new V2SubscriptionApplicationSupport(json, clock); }

	@Transactional(readOnly = true)
	Map<String, Object> subscriptions(long memberId, int page, int size) { int checked=page(page,size); V2SubscriptionData.Page<V2SubscriptionData.DetailProjection> result=store.findSubscriptionList(memberId,checked,size); return Map.of("page",result.page(),"size",result.size(),"totalElements",result.total(),"items",result.items().stream().map(V2SubscriptionData.DetailProjection::body).toList()); }

	@Transactional(readOnly = true)
	V2SubscriptionOperationResult subscription(long memberId, long subscriptionId, int schedulePage, int scheduleSize, int commandPage, int commandSize) {
		page(schedulePage, scheduleSize); page(commandPage, commandSize);
		Map<String, Object> body = detailBody(memberId, subscriptionId, schedulePage, scheduleSize, commandPage, commandSize);
		return new V2SubscriptionOperationResult(200, body, null, "\"" + body.get("version") + "\"", false);
	}

	Map<String, Object> detailBody(long memberId, long subscriptionId, int schedulePage, int scheduleSize, int commandPage, int commandSize) { return store.detailProjection(memberId, subscriptionId, schedulePage, scheduleSize, commandPage, commandSize).body(); }
	private int page(int page,int size){if(page<0||size<1||size>100||page>Integer.MAX_VALUE/size)throw support.validation("page");return page;}
}
