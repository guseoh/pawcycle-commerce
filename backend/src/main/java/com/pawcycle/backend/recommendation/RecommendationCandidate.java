package com.pawcycle.backend.recommendation;

record RecommendationCandidate(
		long productId,
		String name,
		String shortDescription,
		String thumbnailUrl,
		String petType,
		Category category) {

	record Category(long categoryId, String name, String slug) {}
}
