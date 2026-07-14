package com.pawcycle.backend.subscription.application;

import java.time.LocalDate;
import java.util.List;

public record SubscriptionListView(List<SubscriptionSummary> subscriptions) {

	public SubscriptionListView {
		subscriptions = List.copyOf(subscriptions);
	}

	public record SubscriptionSummary(
			Long subscriptionId,
			ProductSummary product,
			SkuSummary sku,
			int quantity,
			int deliveryCycleWeeks,
			LocalDate nextOrderDate) {
	}

	public record ProductSummary(Long productId, String name) {
	}

	public record SkuSummary(Long skuId, String skuName) {
	}
}
