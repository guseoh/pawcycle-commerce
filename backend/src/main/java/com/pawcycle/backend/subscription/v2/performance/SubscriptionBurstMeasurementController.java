package com.pawcycle.backend.subscription.v2.performance;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/performance/subscription-burst")
@Profile("subscription-burst-measurement & !production & !prod")
class SubscriptionBurstMeasurementController {

	private final SubscriptionBurstMeasurementService measurementService;

	SubscriptionBurstMeasurementController(SubscriptionBurstMeasurementService measurementService) {
		this.measurementService = measurementService;
	}

	@PostMapping("/setup")
	SubscriptionBurstMeasurementService.FixtureSummary setup(@RequestParam int cohortSize) {
		return measurementService.setup(cohortSize);
	}

	@PostMapping("/drain")
	SubscriptionBurstMeasurementService.DrainSummary drain() {
		return measurementService.drain();
	}
}
