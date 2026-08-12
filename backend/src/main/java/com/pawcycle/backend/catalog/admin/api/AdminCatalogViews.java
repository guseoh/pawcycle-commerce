package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.product.domain.ProductStatus;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import java.math.BigDecimal;
import java.util.List;

public final class AdminCatalogViews {
	private AdminCatalogViews() {
	}

	public record CategoryList(List<Category> categories) {
	}

	public record Category(Long categoryId, String name, String slug, int displayOrder, boolean active) {
	}

	public record ProductList(List<Product> products) {
	}

	public record Product(
			Long productId,
			Long categoryId,
			String name,
			String shortDescription,
			String description,
			String petType,
			String thumbnailUrl,
			ProductStatus status) {
	}

	public record SkuList(List<Sku> skus) {
	}

	public record Sku(
			Long skuId,
			Long productId,
			String skuCode,
			String name,
			BigDecimal price,
			boolean subscribable,
			int displayOrder,
			SkuStatus status) {
	}
}
