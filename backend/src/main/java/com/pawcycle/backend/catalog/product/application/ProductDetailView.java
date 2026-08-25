package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailView(
		Long productId,
		String name,
		String petType,
		String description,
		String thumbnailUrl,
		CategorySummary category,
		List<SkuDetail> skus,
		boolean purchasable) {

	public ProductDetailView(
			Long productId, String name, String petType, String description, String thumbnailUrl,
			CategorySummary category, List<SkuDetail> skus) {
		this(productId, name, petType, description, thumbnailUrl, category, skus,
				skus.stream().anyMatch(SkuDetail::purchasable));
	}

	public ProductDetailView {
		skus = List.copyOf(skus);
	}

	public record CategorySummary(Long categoryId, String name, String slug) {}

	public record SkuDetail(
			Long skuId,
			String skuName,
			BigDecimal price,
			boolean subscribable,
			List<Integer> availableDeliveryCycles,
			int availableQuantity,
			boolean purchasable) {

		public SkuDetail(
				Long skuId, String skuName, BigDecimal price, boolean subscribable,
				List<Integer> availableDeliveryCycles) {
			this(skuId, skuName, price, subscribable, availableDeliveryCycles, 0, true);
		}

		public SkuDetail {
			availableDeliveryCycles = List.copyOf(availableDeliveryCycles);
		}
	}
}
