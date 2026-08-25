package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.util.List;

public record ProductListView(List<ProductSummary> items, int page, int size, long totalElements, int totalPages) {

	public ProductListView(List<ProductSummary> products) {
		this(products, 0, products.size(), products.size(), products.isEmpty() ? 0 : 1);
	}

	public ProductListView(List<ProductSummary> products, int page, int size, long totalElements) {
		this(products, page, size, totalElements, size == 0 ? 0 : (int) Math.ceil((double) totalElements / size));
	}

	public ProductListView {
		items = List.copyOf(items);
	}

	/** Compatibility accessor for existing application/cache callers; the HTTP contract serializes this collection as {@code items}. */
	public List<ProductSummary> products() {
		return items;
	}

	public record ProductSummary(
			Long productId,
			String name,
			String petType,
			String shortDescription,
			String thumbnailUrl,
			CategorySummary category,
			SkuPriceSummary skuPriceSummary,
			boolean hasSubscribableSku,
			BigDecimal representativePrice,
			boolean purchasable) {

		public ProductSummary(
				Long productId, String name, String petType, String shortDescription, String thumbnailUrl,
				CategorySummary category, SkuPriceSummary skuPriceSummary, boolean hasSubscribableSku) {
			this(productId, name, petType, shortDescription, thumbnailUrl, category, skuPriceSummary,
				hasSubscribableSku,
				skuPriceSummary.skuPrices().isEmpty() ? null : skuPriceSummary.skuPrices().getFirst().price(),
				true);
		}
	}

	public record CategorySummary(Long categoryId, String name, String slug) {}

	public record SkuPriceSummary(List<SkuPrice> skuPrices) {

		public SkuPriceSummary {
			skuPrices = List.copyOf(skuPrices);
		}
	}

	public record SkuPrice(Long skuId, String skuName, BigDecimal price) {
	}
}
