package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
import java.util.List;

public record ProductComparisonFacts(long productId, String name, String thumbnailUrl, String brand, String category,
		BigDecimal representativePrice, BigDecimal compareAtPrice, Integer discountRate, BigDecimal averageRating,
		long reviewCount, boolean subscriptionEligible, boolean purchasable, List<String> facets) {
	public ProductComparisonFacts { facets = List.copyOf(facets == null ? List.of() : facets); }
}
