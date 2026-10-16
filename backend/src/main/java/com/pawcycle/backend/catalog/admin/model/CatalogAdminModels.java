package com.pawcycle.backend.catalog.admin.model;

import java.time.Instant;
import java.util.List;

/** Typed catalog query projections, independent of HTTP contracts. */
public final class CatalogAdminModels {
  private CatalogAdminModels() {}

  public record CategoryFacetListView(Long categoryId, List<CategoryFacetView> facets) {}

  public record CategoryFacetView(Long categoryId, Long facetDefinitionId, int displayOrder) {}

  public record CategoryView(
      Long categoryId, Long parentId, String name, String slug, int displayOrder, boolean active) {}

  public record BrandView(
      Long brandId, String name, String slug, String logoUrl, boolean active, int displayOrder) {}

  public record ImageListView(List<ImageView> images) {}

  public record ImageView(
      Long imageId,
      Long productId,
      String imageUrl,
      String altText,
      int displayOrder,
      String imageType) {}

  public record OptionGroupListView(List<OptionGroupView> optionGroups) {}

  public record OptionGroupView(
      Long optionGroupId,
      Long productId,
      String name,
      int displayOrder,
      List<OptionValueView> values) {}

  public record OptionValueView(
      Long optionValueId, Long optionGroupId, String value, int displayOrder) {}

  public record SkuOptionValuesView(Long skuId, List<Long> optionValueIds) {}

  public record FacetDefinitionListView(List<FacetDefinitionView> facetDefinitions) {}

  public record FacetDefinitionView(
      Long facetDefinitionId, String key, String name, List<FacetOptionView> options) {}

  public record FacetOptionView(
      Long facetOptionId, Long facetDefinitionId, String value, int displayOrder) {}

  public record ProductFacetValuesView(Long productId, List<Long> facetOptionIds) {}

  public record DetailSectionListView(List<DetailSectionView> detailSections) {}

  public record DetailSectionView(
      Long sectionId,
      Long productId,
      String title,
      String body,
      int displayOrder,
      boolean visible,
      Instant createdAt,
      Instant updatedAt) {}
}
