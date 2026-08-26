package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailView(
		Long productId,
		String name,
		String shortDescription,
		String petType,
		String description,
		String thumbnailUrl,
		CategorySummary category,
		List<ProductDetailSectionView> detailSections,
		Trust trust,
		List<SkuDetail> skus,
		boolean purchasable) {

	public ProductDetailView(
			Long productId, String name, String petType, String description, String thumbnailUrl,
			CategorySummary category, List<SkuDetail> skus) {
		this(productId, name, null, petType, description, thumbnailUrl, category, List.of(), Trust.empty(), skus,
				skus.stream().anyMatch(SkuDetail::purchasable));
	}

	public ProductDetailView(
			Long productId, String name, String shortDescription, String petType, String description, String thumbnailUrl,
			CategorySummary category, List<ProductDetailSectionView> detailSections, Trust trust, List<SkuDetail> skus) {
		this(productId, name, shortDescription, petType, description, thumbnailUrl, category, detailSections, trust, skus,
				skus.stream().anyMatch(SkuDetail::purchasable));
	}

	public ProductDetailView {
		detailSections = List.copyOf(detailSections);
		skus = List.copyOf(skus);
		trust = trust == null ? Trust.empty() : trust;
	}

	public record CategorySummary(Long categoryId, String name, String slug) {}

	public record Trust(java.math.BigDecimal averageRating, long reviewCount, long questionCount) {
		public static Trust empty() { return new Trust(java.math.BigDecimal.ZERO, 0, 0); }
	}

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
