package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.util.List;

public record ProductListView(List<ProductSummary> products) {

	public ProductListView {
		products = List.copyOf(products);
	}

	public record ProductSummary(
			Long productId,
			String name,
			String petType,
			String shortDescription,
			String thumbnailUrl,
			SkuPriceSummary skuPriceSummary,
			boolean hasSubscribableSku) {
	}

	public record SkuPriceSummary(List<SkuPrice> skuPrices) {

		public SkuPriceSummary {
			skuPrices = List.copyOf(skuPrices);
		}
	}

	public record SkuPrice(Long skuId, String skuName, BigDecimal price) {
	}
}
