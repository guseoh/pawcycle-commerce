package com.pawcycle.backend.subscription.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionDetailView(
		Long subscriptionId,
		ProductSummary product,
		SkuSummary sku,
		int quantity,
		int deliveryCycleWeeks,
		LocalDate createdDate,
		LocalDate nextOrderDate) {

	public record ProductSummary(Long productId, String name) {
	}

	public record SkuSummary(Long skuId, String skuName, BigDecimal price) {
	}
}
