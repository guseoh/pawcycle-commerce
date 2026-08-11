package com.pawcycle.backend.subscription.v2;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionOrderAutomationMetrics {

	private final MeterRegistry registry;
	private final Counter executions;
	private final Counter processedCandidates;
	private final Counter ordersCreated;
	private final Counter failures;
	private final Counter duplicateNoOps;
	private final Timer duration;

	public SubscriptionOrderAutomationMetrics(MeterRegistry registry) {
		this.registry = registry;
		this.executions = registry.counter("pawcycle.subscription.automation.executions");
		this.processedCandidates = registry.counter("pawcycle.subscription.automation.processed.candidates");
		this.ordersCreated = registry.counter("pawcycle.subscription.automation.orders.created");
		this.failures = registry.counter("pawcycle.subscription.automation.failures");
		this.duplicateNoOps = registry.counter("pawcycle.subscription.automation.duplicate.noop");
		this.duration = registry.timer("pawcycle.subscription.automation.duration");
	}

	Timer.Sample start() {
		return Timer.start(registry);
	}

	void finish(Timer.Sample sample, int processed, int created, int failed, int duplicateOrNoOp) {
		executions.increment();
		increment(processedCandidates, processed);
		increment(ordersCreated, created);
		increment(failures, failed);
		increment(duplicateNoOps, duplicateOrNoOp);
		sample.stop(duration);
	}

	private static void increment(Counter counter, int amount) {
		if (amount > 0) {
			counter.increment(amount);
		}
	}
}
