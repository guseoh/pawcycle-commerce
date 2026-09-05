package com.pawcycle.backend.catalog.admin.persistence;

import java.time.Instant;
import java.util.List;

/** Typed catalog commands and query projections, independent of HTTP contracts. */
public final class CatalogAdminModels {
  private CatalogAdminModels() {}

  public record BrandPatchCommand(
      String name,
      boolean namePresent,
      String slug,
      boolean slugPresent,
      String logoUrl,
      boolean logoUrlPresent,
      Boolean active,
      boolean activePresent,
      Integer displayOrder,
      boolean displayOrderPresent) {}

  public record ImageCreateCommand(
      String imageUrl, String altText, Integer displayOrder, String imageType) {}

  public record ImagePatchCommand(
      String imageUrl,
      boolean imageUrlPresent,
      String altText,
      boolean altTextPresent,
      Integer displayOrder,
      boolean displayOrderPresent,
      String imageType,
      boolean imageTypePresent) {}

  public record OptionGroupCreateCommand(String name, Integer displayOrder) {}

  public record OptionGroupPatchCommand(
      String name, boolean namePresent, Integer displayOrder, boolean displayOrderPresent) {}

  public record OptionValueCreateCommand(String value, Integer displayOrder) {}

  public record OptionValuePatchCommand(
      String value, boolean valuePresent, Integer displayOrder, boolean displayOrderPresent) {}

  public record SkuOptionValuesCommand(java.util.List<Long> optionValueIds) {}

  public record FacetDefinitionCreateCommand(String key, String name) {}

  public record FacetDefinitionPatchCommand(
      String key, boolean keyPresent, String name, boolean namePresent) {}

  public record FacetOptionCreateCommand(String value, Integer displayOrder) {}

  public record FacetOptionPatchCommand(
      String value, boolean valuePresent, Integer displayOrder, boolean displayOrderPresent) {}

  public record CategoryFacetAssignCommand(Integer displayOrder) {}

  public record CategoryFacetListView(Long categoryId, List<CategoryFacetView> facets) {}

  public record CategoryFacetView(Long categoryId, Long facetDefinitionId, int displayOrder) {}

  public record ProductFacetValuesCommand(java.util.List<Long> facetOptionIds) {}

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

  public record DetailSectionCreateCommand(
      String title, String body, Integer displayOrder, Boolean visible) {}

  public record DetailSectionListView(List<DetailSectionView> detailSections) {}

  public record DetailSectionPatchCommand(
      String title,
      boolean titlePresent,
      String body,
      boolean bodyPresent,
      Integer displayOrder,
      boolean displayOrderPresent,
      Boolean visible,
      boolean visiblePresent) {}

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
