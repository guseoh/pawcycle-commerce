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

	public record Category(Long categoryId, Long parentId, String name, String slug, int displayOrder, boolean active) {
		public Category(Long categoryId, String name, String slug, int displayOrder, boolean active) { this(categoryId, null, name, slug, displayOrder, active); }
	}
	public record BrandList(List<Brand> brands) {}
	public record Brand(Long brandId, String name, String slug, String logoUrl, boolean active, int displayOrder) {}
	public record ImageList(List<Image> images) {}
	public record Image(Long imageId, Long productId, String imageUrl, String altText, int displayOrder, String imageType) {}
	public record OptionGroupList(List<OptionGroup> optionGroups) {}
	public record OptionGroup(Long optionGroupId, Long productId, String name, int displayOrder, List<OptionValue> values) {}
	public record OptionValue(Long optionValueId, Long optionGroupId, String value, int displayOrder) {}
	public record SkuOptionValues(Long skuId, List<Long> optionValueIds) {}
	public record FacetDefinitionList(List<FacetDefinition> facetDefinitions) {}
	public record FacetDefinition(Long facetDefinitionId, String key, String name, List<FacetOption> options) {}
	public record FacetOption(Long facetOptionId, Long facetDefinitionId, String value, int displayOrder) {}
	public record CategoryFacet(Long categoryId, Long facetDefinitionId, int displayOrder) {}
	public record ProductFacetValues(Long productId, List<Long> facetOptionIds) {}

	public record ProductList(List<Product> products) {
	}

	public record Product(
			Long productId,
			Long categoryId,
			Long brandId,
			String name,
			String shortDescription,
			String description,
			String petType,
			String thumbnailUrl,
			ProductStatus status) {
		public Product(Long productId, Long categoryId, String name, String shortDescription, String description, String petType, String thumbnailUrl, ProductStatus status) { this(productId, categoryId, 1L, name, shortDescription, description, petType, thumbnailUrl, status); }
	}

	public record SkuList(List<Sku> skus) {
	}

	public record Sku(
			Long skuId,
			Long productId,
			String skuCode,
			String name,
			BigDecimal price,
			BigDecimal compareAtPrice,
			boolean subscribable,
			int displayOrder,
			SkuStatus status) {
		public Sku(Long skuId, Long productId, String skuCode, String name, BigDecimal price, boolean subscribable, int displayOrder, SkuStatus status) { this(skuId, productId, skuCode, name, price, null, subscribable, displayOrder, status); }
	}

	public record DetailSectionList(List<DetailSection> detailSections) {
	}

	public record DetailSection(
			Long sectionId,
			Long productId,
			String title,
			String body,
			int displayOrder,
			boolean visible,
			java.time.Instant createdAt,
			java.time.Instant updatedAt) {
	}
}
