package com.pawcycle.backend.subscription.v2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * This trigger is separate from normal order automation. It never consumes an unprocessed due
 * Schedule and only repairs safely derivable cardinality after an Order already exists.
 */
@Component
@ConditionalOnProperty(prefix = "pawcycle.mvp2.reconciliation", name = "enabled", havingValue = "true")
public class V2ScheduleReconciliationTrigger {

	private final V2SubscriptionReconciliationApplicationService service;

	public V2ScheduleReconciliationTrigger(V2SubscriptionReconciliationApplicationService service) { this.service = service; }

	@Scheduled(fixedDelayString = "${pawcycle.mvp2.reconciliation.fixed-delay-ms:60000}")
	public void reconcileActiveSubscriptions() { service.reconcileActiveSubscriptions(); }
}
