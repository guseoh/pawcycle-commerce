package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.BrandView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryFacetListView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryFacetView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.DetailSectionListView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.DetailSectionView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.FacetDefinitionListView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.FacetDefinitionView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.FacetOptionView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.ImageListView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.ImageView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.OptionGroupListView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.OptionGroupView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.OptionValueView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.ProductFacetValuesView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.SkuOptionValuesView;

final class CatalogAdminResponseMapping {
  private CatalogAdminResponseMapping() {}

  static CategoryFacetListResponse response(CategoryFacetListView value) {
    return new CategoryFacetListResponse(
        value.categoryId(), value.facets().stream().map(CatalogAdminResponseMapping::response).toList());
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
        value.images().stream().map(CatalogAdminResponseMapping::response).toList());
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
        value.optionGroups().stream().map(CatalogAdminResponseMapping::response).toList());
  }

  static OptionGroupResponse response(OptionGroupView value) {
    return new OptionGroupResponse(
        value.optionGroupId(),
        value.productId(),
        value.name(),
        value.displayOrder(),
        value.values().stream().map(CatalogAdminResponseMapping::response).toList());
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
        value.facetDefinitions().stream().map(CatalogAdminResponseMapping::response).toList());
  }

  static FacetDefinitionResponse response(FacetDefinitionView value) {
    return new FacetDefinitionResponse(
        value.facetDefinitionId(),
        value.key(),
        value.name(),
        value.options().stream().map(CatalogAdminResponseMapping::response).toList());
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
        value.detailSections().stream().map(CatalogAdminResponseMapping::response).toList());
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
