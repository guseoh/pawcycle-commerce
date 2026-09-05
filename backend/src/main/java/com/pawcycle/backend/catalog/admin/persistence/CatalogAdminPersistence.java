package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.BrandPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.BrandView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetAssignCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetView;
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
import org.springframework.stereotype.Repository;

/** Compatibility facade preserving the existing application API over split JPA collaborators. */
@Repository
public class CatalogAdminPersistence {
  private final BrandAdminPersistence brands;
  private final ProductImageAdminPersistence images;
  private final ProductOptionAdminPersistence options;
  private final CatalogFacetAdminPersistence facets;

  public CatalogAdminPersistence(
      BrandAdminPersistence brands,
      ProductImageAdminPersistence images,
      ProductOptionAdminPersistence options,
      CatalogFacetAdminPersistence facets) {
    this.brands = brands;
    this.images = images;
    this.options = options;
    this.facets = facets;
  }

  public BrandView brand(long brandId) {
    return brands.brand(brandId);
  }

  public BrandView updateBrand(long brandId, BrandPatchCommand request) {
    return brands.updateBrand(brandId, request);
  }

  public ImageListView images(long productId) {
    return images.images(productId);
  }

  public ImageView createImage(long productId, ImageCreateCommand request) {
    return images.createImage(productId, request);
  }

  public ImageView updateImage(long productId, long imageId, ImagePatchCommand request) {
    return images.updateImage(productId, imageId, request);
  }

  public void deleteImage(long productId, long imageId) {
    images.deleteImage(productId, imageId);
  }

  public OptionGroupListView optionGroups(long productId) {
    return options.optionGroups(productId);
  }

  public OptionGroupView createOptionGroup(long productId, OptionGroupCreateCommand request) {
    return options.createOptionGroup(productId, request);
  }

  public OptionGroupView updateOptionGroup(
      long productId, long groupId, OptionGroupPatchCommand request) {
    return options.updateOptionGroup(productId, groupId, request);
  }

  public void deleteOptionGroup(long productId, long groupId) {
    options.deleteOptionGroup(productId, groupId);
  }

  public OptionValueView createOptionValue(
      long productId, long groupId, OptionValueCreateCommand request) {
    return options.createOptionValue(productId, groupId, request);
  }

  public OptionValueView updateOptionValue(
      long productId, long groupId, long valueId, OptionValuePatchCommand request) {
    return options.updateOptionValue(productId, groupId, valueId, request);
  }

  public void deleteOptionValue(long productId, long groupId, long valueId) {
    options.deleteOptionValue(productId, groupId, valueId);
  }

  public SkuOptionValuesView setSkuOptionValues(
      long productId, long skuId, SkuOptionValuesCommand request) {
    return options.setSkuOptionValues(productId, skuId, request);
  }

  public SkuOptionValuesView skuOptionValues(long productId, long skuId) {
    return options.skuOptionValues(productId, skuId);
  }

  public FacetDefinitionListView facetDefinitions() {
    return facets.facetDefinitions();
  }

  public FacetDefinitionView facetDefinition(long definitionId) {
    return facets.facetDefinition(definitionId);
  }

  public FacetDefinitionView createFacetDefinition(FacetDefinitionCreateCommand request) {
    return facets.createFacetDefinition(request);
  }

  public FacetDefinitionView updateFacetDefinition(
      long definitionId, FacetDefinitionPatchCommand request) {
    return facets.updateFacetDefinition(definitionId, request);
  }

  public void deleteFacetDefinition(long definitionId) {
    facets.deleteFacetDefinition(definitionId);
  }

  public FacetOptionView createFacetOption(long definitionId, FacetOptionCreateCommand request) {
    return facets.createFacetOption(definitionId, request);
  }

  public FacetOptionView updateFacetOption(
      long definitionId, long optionId, FacetOptionPatchCommand request) {
    return facets.updateFacetOption(definitionId, optionId, request);
  }

  public void deleteFacetOption(long definitionId, long optionId) {
    facets.deleteFacetOption(definitionId, optionId);
  }

  public CategoryFacetView assignCategoryFacet(
      long categoryId, long definitionId, CategoryFacetAssignCommand request) {
    return facets.assignCategoryFacet(categoryId, definitionId, request);
  }

  public void removeCategoryFacet(long categoryId, long definitionId) {
    facets.removeCategoryFacet(categoryId, definitionId);
  }

  public ProductFacetValuesView setProductFacetValues(
      long productId, ProductFacetValuesCommand request) {
    return facets.setProductFacetValues(productId, request);
  }

  public ProductFacetValuesView productFacetValues(long productId) {
    return facets.productFacetValues(productId);
  }

  public CategoryFacetListView categoryFacets(long categoryId) {
    return facets.categoryFacets(categoryId);
  }
}
