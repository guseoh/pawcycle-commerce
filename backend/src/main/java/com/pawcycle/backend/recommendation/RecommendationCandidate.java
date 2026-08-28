package com.pawcycle.backend.recommendation;

record RecommendationCandidate(
		long productId,
		String name,
		String shortDescription,
		String thumbnailUrl,
		String petType,
		Category category,
		Brand brand,
		java.util.List<String> facets,
		long popularScore) {

	RecommendationCandidate(long productId, String name, String shortDescription, String thumbnailUrl, String petType, Category category) {
		this(productId, name, shortDescription, thumbnailUrl, petType, category, null, java.util.List.of(), 0);
	}

	RecommendationCandidate {
		facets = java.util.List.copyOf(facets == null ? java.util.List.of() : facets);
	}

	record Category(long categoryId, String name, String slug) {}
	record Brand(long brandId, String name, String slug) {}
}
