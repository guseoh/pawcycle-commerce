package com.pawcycle.backend.recommendation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
class RecommendationMetrics {
	private final Counter success;
	private final Counter fallback;
	private final Timer aiCall;

	RecommendationMetrics(MeterRegistry registry) {
		success = registry.counter("pawcycle.recommendation.ai.outcomes", "result", "success");
		fallback = registry.counter("pawcycle.recommendation.ai.outcomes", "result", "fallback");
		aiCall = registry.timer("pawcycle.recommendation.ai.call");
	}

	void success() { success.increment(); }
	void fallback() { fallback.increment(); }
	<T> T recordAiCall(Supplier<T> call) { return aiCall.record(call); }
}
