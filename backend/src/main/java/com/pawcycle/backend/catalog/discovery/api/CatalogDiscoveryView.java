package com.pawcycle.backend.catalog.discovery.api;

import java.util.List;

public record CatalogDiscoveryView(
		List<Category> categories,
		List<Brand> brands,
		List<CategoryFacets> categoryFacets) {

	public CatalogDiscoveryView {
		categories = List.copyOf(categories == null ? List.of() : categories);
		brands = List.copyOf(brands == null ? List.of() : brands);
		categoryFacets = List.copyOf(categoryFacets == null ? List.of() : categoryFacets);
	}

	public record Category(
			Long categoryId,
			String name,
			String slug,
			int displayOrder,
			List<Child> children) {
		public Category {
			children = List.copyOf(children == null ? List.of() : children);
		}
	}

	public record Child(Long categoryId, String name, String slug, int displayOrder) {
	}

	public record Brand(Long brandId, String name, String slug, String logoUrl, int displayOrder) {
	}

	public record CategoryFacets(String categorySlug, List<Facet> facets) {
		public CategoryFacets {
			facets = List.copyOf(facets == null ? List.of() : facets);
		}
	}

	public record Facet(String key, String name, int displayOrder, List<Option> options) {
		public Facet {
			options = List.copyOf(options == null ? List.of() : options);
		}
	}

	public record Option(Long optionId, String value, int displayOrder) {
	}
}
