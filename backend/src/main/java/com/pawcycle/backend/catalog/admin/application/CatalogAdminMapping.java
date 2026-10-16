package com.pawcycle.backend.catalog.admin.application;

import com.pawcycle.backend.catalog.admin.api.BrandResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryFacetListResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryFacetResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryResponse;
import com.pawcycle.backend.catalog.admin.api.DetailSectionListResponse;
import com.pawcycle.backend.catalog.admin.api.DetailSectionResponse;
import com.pawcycle.backend.catalog.admin.api.FacetDefinitionListResponse;
import com.pawcycle.backend.catalog.admin.api.FacetDefinitionResponse;
import com.pawcycle.backend.catalog.admin.api.FacetOptionResponse;
import com.pawcycle.backend.catalog.admin.api.ImageListResponse;
import com.pawcycle.backend.catalog.admin.api.ImageResponse;
import com.pawcycle.backend.catalog.admin.api.OptionGroupListResponse;
import com.pawcycle.backend.catalog.admin.api.OptionGroupResponse;
import com.pawcycle.backend.catalog.admin.api.OptionValueResponse;
import com.pawcycle.backend.catalog.admin.api.ProductFacetValuesResponse;
import com.pawcycle.backend.catalog.admin.api.SkuOptionValuesResponse;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.BrandView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.DetailSectionListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.DetailSectionView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetDefinitionListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetDefinitionView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetOptionView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ImageListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ImageView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionGroupListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionGroupView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionValueView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ProductFacetValuesView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.SkuOptionValuesView;

final class CatalogAdminMapping {
  private CatalogAdminMapping() {}

  static CategoryFacetListResponse response(CategoryFacetListView value) {
    return new CategoryFacetListResponse(
        value.categoryId(), value.facets().stream().map(CatalogAdminMapping::response).toList());
  }

  static CategoryFacetResponse response(CategoryFacetView value) {
    return new CategoryFacetResponse(
        value.categoryId(), value.facetDefinitionId(), value.displayOrder());
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

  static DetailSectionListResponse response(DetailSectionListView value) {
    return new DetailSectionListResponse(
        value.detailSections().stream().map(CatalogAdminMapping::response).toList());
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
