package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.BrandPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.CategoryFacetAssignCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.DetailSectionCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.DetailSectionPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.FacetDefinitionCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.FacetDefinitionPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.FacetOptionCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.FacetOptionPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ImageCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ImagePatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.OptionGroupCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.OptionGroupPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.OptionValueCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.OptionValuePatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ProductFacetValuesCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.SkuOptionValuesCommand;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.BrandCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.CategoryCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.CategoryPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ProductCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ProductPatchCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.SkuCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.SkuPatchCommand;

final class CatalogAdminCommandMapping {
  private CatalogAdminCommandMapping() {}

  static BrandCreateCommand command(BrandCreateRequest value) {
    return new BrandCreateCommand(
        value.name(),
        value.slug(),
        value.logoUrl(),
        value.active(),
        value.displayOrder());
  }

  static CategoryCreateCommand command(CategoryCreateRequest value) {
    return new CategoryCreateCommand(
        value.name(),
        value.slug(),
        value.displayOrder(),
        value.active(),
        value.parentId());
  }

  static CategoryPatchCommand command(CategoryPatchRequest value) {
    return new CategoryPatchCommand(
        value.getName(),
        value.isNamePresent(),
        value.getSlug(),
        value.isSlugPresent(),
        value.getDisplayOrder(),
        value.isDisplayOrderPresent(),
        value.getActive(),
        value.isActivePresent(),
        value.getParentId(),
        value.isParentIdPresent());
  }

  static ProductCreateCommand command(ProductCreateRequest value) {
    return new ProductCreateCommand(
        value.categoryId(),
        value.brandId(),
        value.name(),
        value.shortDescription(),
        value.description(),
        value.petType(),
        value.thumbnailUrl());
  }

  static ProductPatchCommand command(ProductPatchRequest value) {
    return new ProductPatchCommand(
        value.getCategoryId(),
        value.isCategoryIdPresent(),
        value.getBrandId(),
        value.isBrandIdPresent(),
        value.getName(),
        value.isNamePresent(),
        value.getShortDescription(),
        value.isShortDescriptionPresent(),
        value.getDescription(),
        value.isDescriptionPresent(),
        value.getPetType(),
        value.isPetTypePresent(),
        value.getThumbnailUrl(),
        value.isThumbnailUrlPresent(),
        value.getStatus(),
        value.isStatusPresent());
  }

  static SkuCreateCommand command(SkuCreateRequest value) {
    return new SkuCreateCommand(
        value.skuCode(),
        value.name(),
        value.price(),
        value.compareAtPrice(),
        value.subscribable(),
        value.displayOrder(),
        value.status());
  }

  static SkuPatchCommand command(SkuPatchRequest value) {
    return new SkuPatchCommand(
        value.getName(),
        value.isNamePresent(),
        value.getPrice(),
        value.isPricePresent(),
        value.getCompareAtPrice(),
        value.isCompareAtPricePresent(),
        value.getSubscribable(),
        value.isSubscribablePresent(),
        value.getDisplayOrder(),
        value.isDisplayOrderPresent(),
        value.getStatus(),
        value.isStatusPresent());
  }

  static BrandPatchCommand command(BrandPatchRequest value) {
    return new BrandPatchCommand(
        value.getName(),
        value.isNamePresent(),
        value.getSlug(),
        value.isSlugPresent(),
        value.getLogoUrl(),
        value.isLogoUrlPresent(),
        value.getActive(),
        value.isActivePresent(),
        value.getDisplayOrder(),
        value.isDisplayOrderPresent());
  }

  static ImageCreateCommand command(ImageCreateRequest value) {
    return new ImageCreateCommand(
        value.imageUrl(), value.altText(), value.displayOrder(), value.imageType());
  }

  static ImagePatchCommand command(ImagePatchRequest value) {
    return new ImagePatchCommand(
        value.getImageUrl(),
        value.isImageUrlPresent(),
        value.getAltText(),
        value.isAltTextPresent(),
        value.getDisplayOrder(),
        value.isDisplayOrderPresent(),
        value.getImageType(),
        value.isImageTypePresent());
  }

  static OptionGroupCreateCommand command(OptionGroupCreateRequest value) {
    return new OptionGroupCreateCommand(value.name(), value.displayOrder());
  }

  static OptionGroupPatchCommand command(OptionGroupPatchRequest value) {
    return new OptionGroupPatchCommand(
        value.getName(),
        value.isNamePresent(),
        value.getDisplayOrder(),
        value.isDisplayOrderPresent());
  }

  static OptionValueCreateCommand command(OptionValueCreateRequest value) {
    return new OptionValueCreateCommand(value.value(), value.displayOrder());
  }

  static OptionValuePatchCommand command(OptionValuePatchRequest value) {
    return new OptionValuePatchCommand(
        value.getValue(),
        value.isValuePresent(),
        value.getDisplayOrder(),
        value.isDisplayOrderPresent());
  }

  static SkuOptionValuesCommand command(SkuOptionValuesRequest value) {
    return new SkuOptionValuesCommand(value.optionValueIds());
  }

  static FacetDefinitionCreateCommand command(FacetDefinitionCreateRequest value) {
    return new FacetDefinitionCreateCommand(value.key(), value.name());
  }

  static FacetDefinitionPatchCommand command(FacetDefinitionPatchRequest value) {
    return new FacetDefinitionPatchCommand(
        value.getKey(), value.isKeyPresent(), value.getName(), value.isNamePresent());
  }

  static FacetOptionCreateCommand command(FacetOptionCreateRequest value) {
    return new FacetOptionCreateCommand(value.value(), value.displayOrder());
  }

  static FacetOptionPatchCommand command(FacetOptionPatchRequest value) {
    return new FacetOptionPatchCommand(
        value.getValue(),
        value.isValuePresent(),
        value.getDisplayOrder(),
        value.isDisplayOrderPresent());
  }

  static CategoryFacetAssignCommand command(CategoryFacetAssignRequest value) {
    return new CategoryFacetAssignCommand(value.displayOrder());
  }

  static ProductFacetValuesCommand command(ProductFacetValuesRequest value) {
    return new ProductFacetValuesCommand(value.facetOptionIds());
  }

  static DetailSectionCreateCommand command(DetailSectionCreateRequest value) {
    return new DetailSectionCreateCommand(
        value.title(), value.body(), value.displayOrder(), value.visible());
  }

  static DetailSectionPatchCommand command(DetailSectionPatchRequest value) {
    return new DetailSectionPatchCommand(
        value.getTitle(),
        value.isTitlePresent(),
        value.getBody(),
        value.isBodyPresent(),
        value.getDisplayOrder(),
        value.isDisplayOrderPresent(),
        value.getVisible(),
        value.isVisiblePresent());
  }
}
