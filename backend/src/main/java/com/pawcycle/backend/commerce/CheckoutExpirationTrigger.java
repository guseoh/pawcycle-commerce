package com.pawcycle.backend.commerce;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		prefix = "pawcycle.commerce.checkout-expiration",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true)
public class CheckoutExpirationTrigger {
	private final CheckoutExpirationService service;
	private final int batchSize;

	public CheckoutExpirationTrigger(
			CheckoutExpirationService service,
			@Value("${pawcycle.commerce.checkout-expiration.batch-size:100}") int batchSize) {
		if (batchSize < 1) throw new IllegalArgumentException("checkout expiration batch-size must be positive");
		this.service = service;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${pawcycle.commerce.checkout-expiration.fixed-delay-ms:60000}")
	public void expireDueCheckouts() {
		service.expireDue(batchSize);
	}
}
