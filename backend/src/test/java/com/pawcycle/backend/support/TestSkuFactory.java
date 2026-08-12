package com.pawcycle.backend.support;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import java.math.BigDecimal;
import java.util.UUID;

public final class TestSkuFactory {
	private TestSkuFactory() {
	}

	public static Sku sku(
			Product product,
			String name,
			BigDecimal price,
			boolean subscribable,
			int displayOrder) {
		return sku(product, "TEST-" + UUID.randomUUID(), name, price, subscribable, displayOrder);
	}

	public static Sku sku(
			Product product,
			String skuCode,
			String name,
			BigDecimal price,
			boolean subscribable,
			int displayOrder) {
		return new Sku(
				product,
				skuCode,
				name,
				price,
				subscribable,
				displayOrder,
				SkuStatus.ACTIVE);
	}
}
