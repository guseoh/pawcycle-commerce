package com.pawcycle.backend.subscription.v2;

import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class V2SubscriptionReconciliationApplicationService {
	private static final Logger log = LoggerFactory.getLogger(V2SubscriptionReconciliationApplicationService.class);
	private final V2SubscriptionJdbcStore store;
	private final V2SubscriptionMetrics metrics;
	private final TransactionTemplate transaction;

	V2SubscriptionReconciliationApplicationService(
			V2SubscriptionJdbcStore store,
			V2SubscriptionMetrics metrics,
			PlatformTransactionManager transactionManager) {
		this.store = store;
		this.metrics = metrics;
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	void reconcileActiveSubscriptions() {
		Timer.Sample sample = metrics.startReconciliation();
		int processed = 0;
		int failures = 0;
		try {
			List<Long> active = store.activeSubscriptionIds();
			for (Long subscriptionId : active) {
				processed++;
				try {
					transaction.executeWithoutResult(status -> store.reconcileActiveSubscription(subscriptionId));
				} catch (RuntimeException exception) {
					failures++;
					log.error("Subscription reconciliation failed; subscriptionId={}", subscriptionId, exception);
				}
			}
		} catch (RuntimeException exception) {
			failures++;
			throw exception;
		} finally {
			metrics.finishReconciliation(sample, processed, failures);
		}
	}
}
