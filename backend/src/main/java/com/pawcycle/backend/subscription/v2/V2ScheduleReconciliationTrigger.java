package com.pawcycle.backend.subscription.v2;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The trigger has no order, payment, inventory, or delivery responsibility.  It only preserves
 * the approved Schedule cardinality for ACTIVE V2 subscriptions that receive no write command.
 */
@Component
public class V2ScheduleReconciliationTrigger {

	private final V2SubscriptionService service;

	public V2ScheduleReconciliationTrigger(V2SubscriptionService service) { this.service = service; }

	@Scheduled(fixedDelayString = "${pawcycle.mvp2.reconciliation.fixed-delay-ms:60000}")
	public void reconcileActiveSubscriptions() { service.reconcileActiveSubscriptions(); }
}
