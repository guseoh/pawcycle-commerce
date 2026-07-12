package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailView(
		Long productId,
		String name,
		String petType,
		String description,
		String thumbnailUrl,
		List<SkuDetail> skus) {

	public ProductDetailView {
		skus = List.copyOf(skus);
	}

	public record SkuDetail(
			Long skuId,
			String skuName,
			BigDecimal price,
			boolean subscribable,
			List<Integer> availableDeliveryCycles) {

		public SkuDetail {
			availableDeliveryCycles = List.copyOf(availableDeliveryCycles);
		}
	}
}
