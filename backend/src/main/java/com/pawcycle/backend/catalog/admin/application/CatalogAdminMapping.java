package com.pawcycle.backend.catalog.admin.application;

import com.pawcycle.backend.catalog.admin.api.BrandPatchRequest;
import com.pawcycle.backend.catalog.admin.api.BrandResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryFacetAssignRequest;
import com.pawcycle.backend.catalog.admin.api.CategoryFacetListResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryFacetResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryResponse;
import com.pawcycle.backend.catalog.admin.api.DetailSectionCreateRequest;
import com.pawcycle.backend.catalog.admin.api.DetailSectionListResponse;
import com.pawcycle.backend.catalog.admin.api.DetailSectionPatchRequest;
import com.pawcycle.backend.catalog.admin.api.DetailSectionResponse;
import com.pawcycle.backend.catalog.admin.api.FacetDefinitionCreateRequest;
import com.pawcycle.backend.catalog.admin.api.FacetDefinitionListResponse;
import com.pawcycle.backend.catalog.admin.api.FacetDefinitionPatchRequest;
import com.pawcycle.backend.catalog.admin.api.FacetDefinitionResponse;
import com.pawcycle.backend.catalog.admin.api.FacetOptionCreateRequest;
import com.pawcycle.backend.catalog.admin.api.FacetOptionPatchRequest;
import com.pawcycle.backend.catalog.admin.api.FacetOptionResponse;
import com.pawcycle.backend.catalog.admin.api.ImageCreateRequest;
import com.pawcycle.backend.catalog.admin.api.ImageListResponse;
import com.pawcycle.backend.catalog.admin.api.ImagePatchRequest;
import com.pawcycle.backend.catalog.admin.api.ImageResponse;
import com.pawcycle.backend.catalog.admin.api.OptionGroupCreateRequest;
import com.pawcycle.backend.catalog.admin.api.OptionGroupListResponse;
import com.pawcycle.backend.catalog.admin.api.OptionGroupPatchRequest;
import com.pawcycle.backend.catalog.admin.api.OptionGroupResponse;
import com.pawcycle.backend.catalog.admin.api.OptionValueCreateRequest;
import com.pawcycle.backend.catalog.admin.api.OptionValuePatchRequest;
import com.pawcycle.backend.catalog.admin.api.OptionValueResponse;
import com.pawcycle.backend.catalog.admin.api.ProductFacetValuesRequest;
import com.pawcycle.backend.catalog.admin.api.ProductFacetValuesResponse;
import com.pawcycle.backend.catalog.admin.api.SkuOptionValuesRequest;
import com.pawcycle.backend.catalog.admin.api.SkuOptionValuesResponse;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.BrandPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.BrandView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetAssignCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.DetailSectionCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.DetailSectionListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.DetailSectionPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.DetailSectionView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetDefinitionCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetDefinitionListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetDefinitionPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetDefinitionView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetOptionCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetOptionPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetOptionView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ImageCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ImageListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ImagePatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ImageView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionGroupCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionGroupListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionGroupPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionGroupView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionValueCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionValuePatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionValueView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ProductFacetValuesCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ProductFacetValuesView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.SkuOptionValuesCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.SkuOptionValuesView;

final class CatalogAdminMapping {
  private CatalogAdminMapping() {}

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

  static CategoryFacetListResponse response(CategoryFacetListView value) {
    return new CategoryFacetListResponse(
        value.categoryId(), value.facets().stream().map(CatalogAdminMapping::response).toList());
  }

  static CategoryFacetResponse response(CategoryFacetView value) {
    return new CategoryFacetResponse(
        value.categoryId(), value.facetDefinitionId(), value.displayOrder());
  }

  static ProductFacetValuesCommand command(ProductFacetValuesRequest value) {
    return new ProductFacetValuesCommand(value.facetOptionIds());
  }

  static CategoryResponse response(CategoryView value) {
    return new CategoryResponse(
        value.categoryId(),
        value.parentId(),
        value.name(),
        value.slug(),
        value.displayOrder(),
        value.active());
  }

  static BrandResponse response(BrandView value) {
    return new BrandResponse(
        value.brandId(),
        value.name(),
        value.slug(),
        value.logoUrl(),
        value.active(),
        value.displayOrder());
  }

  static ImageListResponse response(ImageListView value) {
    return new ImageListResponse(
        value.images().stream().map(CatalogAdminMapping::response).toList());
  }

  static ImageResponse response(ImageView value) {
    return new ImageResponse(
        value.imageId(),
        value.productId(),
        value.imageUrl(),
        value.altText(),
        value.displayOrder(),
        value.imageType());
  }

  static OptionGroupListResponse response(OptionGroupListView value) {
    return new OptionGroupListResponse(
        value.optionGroups().stream().map(CatalogAdminMapping::response).toList());
  }

  static OptionGroupResponse response(OptionGroupView value) {
    return new OptionGroupResponse(
        value.optionGroupId(),
        value.productId(),
        value.name(),
        value.displayOrder(),
        value.values().stream().map(CatalogAdminMapping::response).toList());
  }

  static OptionValueResponse response(OptionValueView value) {
    return new OptionValueResponse(
        value.optionValueId(), value.optionGroupId(), value.value(), value.displayOrder());
  }

  static SkuOptionValuesResponse response(SkuOptionValuesView value) {
    return new SkuOptionValuesResponse(value.skuId(), value.optionValueIds());
  }

  static FacetDefinitionListResponse response(FacetDefinitionListView value) {
    return new FacetDefinitionListResponse(
        value.facetDefinitions().stream().map(CatalogAdminMapping::response).toList());
  }

  static FacetDefinitionResponse response(FacetDefinitionView value) {
    return new FacetDefinitionResponse(
        value.facetDefinitionId(),
        value.key(),
        value.name(),
        value.options().stream().map(CatalogAdminMapping::response).toList());
  }

  static FacetOptionResponse response(FacetOptionView value) {
    return new FacetOptionResponse(
        value.facetOptionId(), value.facetDefinitionId(), value.value(), value.displayOrder());
  }

  static ProductFacetValuesResponse response(ProductFacetValuesView value) {
    return new ProductFacetValuesResponse(value.productId(), value.facetOptionIds());
  }

  static DetailSectionCreateCommand command(DetailSectionCreateRequest value) {
    return new DetailSectionCreateCommand(
        value.title(), value.body(), value.displayOrder(), value.visible());
  }

  static DetailSectionListResponse response(DetailSectionListView value) {
    return new DetailSectionListResponse(
        value.detailSections().stream().map(CatalogAdminMapping::response).toList());
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

  static DetailSectionResponse response(DetailSectionView value) {
    return new DetailSectionResponse(
        value.sectionId(),
        value.productId(),
        value.title(),
        value.body(),
        value.displayOrder(),
        value.visible(),
        value.createdAt(),
        value.updatedAt());
  }
}
