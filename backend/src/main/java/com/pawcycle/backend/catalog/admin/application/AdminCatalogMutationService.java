package com.pawcycle.backend.catalog.admin.application;


import com.pawcycle.backend.catalog.admin.api.BrandCreateRequest;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.BrandPatchCommand;
import com.pawcycle.backend.catalog.admin.api.BrandResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryCreateRequest;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.CategoryFacetAssignCommand;
import com.pawcycle.backend.catalog.admin.api.CategoryPatchRequest;
import com.pawcycle.backend.catalog.admin.api.CategoryResponse;
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
import com.pawcycle.backend.catalog.admin.api.ProductCreateRequest;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ProductFacetValuesCommand;
import com.pawcycle.backend.catalog.admin.api.ProductPatchRequest;
import com.pawcycle.backend.catalog.admin.api.ProductResponse;
import com.pawcycle.backend.catalog.admin.api.SkuCreateRequest;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.SkuOptionValuesCommand;
import com.pawcycle.backend.catalog.admin.api.SkuPatchRequest;
import com.pawcycle.backend.catalog.admin.api.SkuResponse;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.BrandView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryFacetListView;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryFacetView;
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
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminPersistence;
import com.pawcycle.backend.catalog.admin.persistence.ProductDetailSectionPersistence;
import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the transaction and audit unit for administrator catalog mutations. */
@Service
public class AdminCatalogMutationService {
  private final AdminAuditService audits;

  private final AdminCatalogService adminCatalogService;
  private final CatalogAdminPersistence catalogExpansionAdminService;
  private final ProductDetailSectionPersistence productDetailSectionService;

  public AdminCatalogMutationService(
      AdminAuditService audits,
      AdminCatalogService adminCatalogService,
      CatalogAdminPersistence catalogExpansionAdminService,
      ProductDetailSectionPersistence productDetailSectionService) {
    this.adminCatalogService = adminCatalogService;
    this.catalogExpansionAdminService = catalogExpansionAdminService;
    this.productDetailSectionService = productDetailSectionService;
    this.audits = audits;
  }

  private <T> T execute(
      long adminId,
      String action,
      String referenceType,
      Supplier<T> mutation,
      ToLongFunction<T> referenceId) {
    T result = mutation.get();
    audits.append(adminId, action, referenceType, referenceId.applyAsLong(result));
    return result;
  }

  private void execute(
      long adminId, String action, String referenceType, long referenceId, Runnable mutation) {
    mutation.run();
    audits.append(adminId, action, referenceType, referenceId);
  }

  @Transactional
  public BrandResponse createBrand(long adminId, BrandCreateRequest request) {
    return execute(
        adminId,
        "CATALOG_BRAND_CREATE",
        "BRAND",
        () -> adminCatalogService.createBrand(request),
        BrandResponse::brandId);
  }

  @Transactional
  public BrandView updateBrand(long adminId, long brandId, BrandPatchCommand request) {
    return execute(
        adminId,
        "CATALOG_BRAND_UPDATE",
        "BRAND",
        () -> catalogExpansionAdminService.updateBrand(brandId, request),
        ignored -> brandId);
  }

  @Transactional
  public CategoryResponse createCategory(long adminId, CategoryCreateRequest request) {
    return execute(
        adminId,
        "CATALOG_CATEGORY_CREATE",
        "CATEGORY",
        () -> adminCatalogService.createCategory(request),
        CategoryResponse::categoryId);
  }

  @Transactional
  public CategoryResponse updateCategory(
      long adminId, Long categoryId, CategoryPatchRequest request) {
    return execute(
        adminId,
        "CATALOG_CATEGORY_UPDATE",
        "CATEGORY",
        () -> adminCatalogService.updateCategory(categoryId, request),
        ignored -> categoryId);
  }

  @Transactional
  public ProductResponse createProduct(long adminId, ProductCreateRequest request) {
    return execute(
        adminId,
        "CATALOG_PRODUCT_CREATE",
        "PRODUCT",
        () -> adminCatalogService.createProduct(request),
        ProductResponse::productId);
  }

  @Transactional
  public ProductResponse updateProduct(long adminId, Long productId, ProductPatchRequest request) {
    return execute(
        adminId,
        "CATALOG_PRODUCT_UPDATE",
        "PRODUCT",
        () -> adminCatalogService.updateProduct(productId, request),
        ignored -> productId);
  }

  @Transactional
  public SkuResponse createSku(long adminId, Long productId, SkuCreateRequest request) {
    return execute(
        adminId,
        "CATALOG_SKU_CREATE",
        "SKU",
        () -> adminCatalogService.createSku(productId, request),
        SkuResponse::skuId);
  }

  @Transactional
  public SkuResponse updateSku(long adminId, Long productId, Long skuId, SkuPatchRequest request) {
    return execute(
        adminId,
        "CATALOG_SKU_UPDATE",
        "SKU",
        () -> adminCatalogService.updateSku(productId, skuId, request),
        ignored -> skuId);
  }

  @Transactional
  public ImageView createImage(long adminId, long productId, ImageCreateCommand request) {
    return execute(
        adminId,
        "CATALOG_PRODUCT_IMAGE_CREATE",
        "PRODUCT_IMAGE",
        () -> catalogExpansionAdminService.createImage(productId, request),
        ImageView::imageId);
  }

  @Transactional
  public ImageView updateImage(
      long adminId, long productId, long imageId, ImagePatchCommand request) {
    return execute(
        adminId,
        "CATALOG_PRODUCT_IMAGE_UPDATE",
        "PRODUCT_IMAGE",
        () ->
            catalogExpansionAdminService.updateImage(productId, imageId, request),
        ignored -> imageId);
  }

  @Transactional
  public void deleteImage(long adminId, long productId, long imageId) {
    execute(
        adminId,
        "CATALOG_PRODUCT_IMAGE_DELETE",
        "PRODUCT_IMAGE",
        imageId,
        () -> catalogExpansionAdminService.deleteImage(productId, imageId));
  }

  @Transactional
  public OptionGroupView createOptionGroup(
      long adminId, long productId, OptionGroupCreateCommand request) {
    return execute(
        adminId,
        "CATALOG_OPTION_GROUP_CREATE",
        "PRODUCT_OPTION_GROUP",
        () -> catalogExpansionAdminService.createOptionGroup(productId, request),
        OptionGroupView::optionGroupId);
  }

  @Transactional
  public OptionGroupView updateOptionGroup(
      long adminId, long productId, long groupId, OptionGroupPatchCommand request) {
    return execute(
        adminId,
        "CATALOG_OPTION_GROUP_UPDATE",
        "PRODUCT_OPTION_GROUP",
        () ->
            catalogExpansionAdminService.updateOptionGroup(
                    productId, groupId, request),
        ignored -> groupId);
  }

  @Transactional
  public void deleteOptionGroup(long adminId, long productId, long groupId) {
    execute(
        adminId,
        "CATALOG_OPTION_GROUP_DELETE",
        "PRODUCT_OPTION_GROUP",
        groupId,
        () -> catalogExpansionAdminService.deleteOptionGroup(productId, groupId));
  }

  @Transactional
  public OptionValueView createOptionValue(
      long adminId, long productId, long groupId, OptionValueCreateCommand request) {
    return execute(
        adminId,
        "CATALOG_OPTION_VALUE_CREATE",
        "PRODUCT_OPTION_VALUE",
        () ->
            catalogExpansionAdminService.createOptionValue(
                    productId, groupId, request),
        OptionValueView::optionValueId);
  }

  @Transactional
  public OptionValueView updateOptionValue(
      long adminId, long productId, long groupId, long valueId, OptionValuePatchCommand request) {
    return execute(
        adminId,
        "CATALOG_OPTION_VALUE_UPDATE",
        "PRODUCT_OPTION_VALUE",
        () ->
            catalogExpansionAdminService.updateOptionValue(
                    productId, groupId, valueId, request),
        ignored -> valueId);
  }

  @Transactional
  public void deleteOptionValue(long adminId, long productId, long groupId, long valueId) {
    execute(
        adminId,
        "CATALOG_OPTION_VALUE_DELETE",
        "PRODUCT_OPTION_VALUE",
        valueId,
        () -> catalogExpansionAdminService.deleteOptionValue(productId, groupId, valueId));
  }

  @Transactional
  public SkuOptionValuesView setSkuOptionValues(
      long adminId, long productId, long skuId, SkuOptionValuesCommand request) {
    return execute(
        adminId,
        "CATALOG_SKU_OPTION_VALUES_SET",
        "SKU",
        () ->
            catalogExpansionAdminService.setSkuOptionValues(
                    productId, skuId, request),
        ignored -> skuId);
  }

  @Transactional
  public FacetDefinitionView createFacetDefinition(
      long adminId, FacetDefinitionCreateCommand request) {
    return execute(
        adminId,
        "CATALOG_FACET_CREATE",
        "FACET_DEFINITION",
        () -> catalogExpansionAdminService.createFacetDefinition(request),
        FacetDefinitionView::facetDefinitionId);
  }

  @Transactional
  public FacetDefinitionView updateFacetDefinition(
      long adminId, long definitionId, FacetDefinitionPatchCommand request) {
    return execute(
        adminId,
        "CATALOG_FACET_UPDATE",
        "FACET_DEFINITION",
        () ->
            catalogExpansionAdminService.updateFacetDefinition(definitionId, request),
        ignored -> definitionId);
  }

  @Transactional
  public void deleteFacetDefinition(long adminId, long definitionId) {
    execute(
        adminId,
        "CATALOG_FACET_DELETE",
        "FACET_DEFINITION",
        definitionId,
        () -> catalogExpansionAdminService.deleteFacetDefinition(definitionId));
  }

  @Transactional
  public FacetOptionView createFacetOption(
      long adminId, long definitionId, FacetOptionCreateCommand request) {
    return execute(
        adminId,
        "CATALOG_FACET_OPTION_CREATE",
        "FACET_OPTION",
        () ->
            catalogExpansionAdminService.createFacetOption(definitionId, request),
        FacetOptionView::facetOptionId);
  }

  @Transactional
  public FacetOptionView updateFacetOption(
      long adminId, long definitionId, long optionId, FacetOptionPatchCommand request) {
    return execute(
        adminId,
        "CATALOG_FACET_OPTION_UPDATE",
        "FACET_OPTION",
        () ->
            catalogExpansionAdminService.updateFacetOption(
                    definitionId, optionId, request),
        ignored -> optionId);
  }

  @Transactional
  public void deleteFacetOption(long adminId, long definitionId, long optionId) {
    execute(
        adminId,
        "CATALOG_FACET_OPTION_DELETE",
        "FACET_OPTION",
        optionId,
        () -> catalogExpansionAdminService.deleteFacetOption(definitionId, optionId));
  }

  @Transactional
  public CategoryFacetView assignCategoryFacet(
      long adminId, long categoryId, long definitionId, CategoryFacetAssignCommand request) {
    return execute(
        adminId,
        "CATALOG_CATEGORY_FACET_SET",
        "CATEGORY",
        () ->
            catalogExpansionAdminService.assignCategoryFacet(
                    categoryId, definitionId, request),
        ignored -> categoryId);
  }

  @Transactional
  public void removeCategoryFacet(long adminId, long categoryId, long definitionId) {
    execute(
        adminId,
        "CATALOG_CATEGORY_FACET_DELETE",
        "CATEGORY",
        categoryId,
        () -> catalogExpansionAdminService.removeCategoryFacet(categoryId, definitionId));
  }

  @Transactional
  public ProductFacetValuesView setProductFacetValues(
      long adminId, long productId, ProductFacetValuesCommand request) {
    return execute(
        adminId,
        "CATALOG_PRODUCT_FACET_VALUES_SET",
        "PRODUCT",
        () ->
            catalogExpansionAdminService.setProductFacetValues(productId, request),
        ignored -> productId);
  }

  @Transactional
  public DetailSectionView createDetailSection(
      long adminId, Long productId, DetailSectionCreateCommand request) {
    return execute(
        adminId,
        "CATALOG_DETAIL_SECTION_CREATE",
        "PRODUCT_DETAIL_SECTION",
        () -> productDetailSectionService.create(productId, request),
        DetailSectionView::sectionId);
  }

  @Transactional
  public DetailSectionView updateDetailSection(
      long adminId, Long productId, Long sectionId, DetailSectionPatchCommand request) {
    return execute(
        adminId,
        "CATALOG_DETAIL_SECTION_UPDATE",
        "PRODUCT_DETAIL_SECTION",
        () -> productDetailSectionService.update(productId, sectionId, request),
        ignored -> sectionId);
  }

  @Transactional
  public void deleteDetailSection(long adminId, Long productId, Long sectionId) {
    execute(
        adminId,
        "CATALOG_DETAIL_SECTION_DELETE",
        "PRODUCT_DETAIL_SECTION",
        sectionId,
        () -> productDetailSectionService.delete(productId, sectionId));
  }

  @Transactional(readOnly = true)
  public BrandView brand(long brandId) {
    return catalogExpansionAdminService.brand(brandId);
  }

  @Transactional(readOnly = true)
  public ImageListView images(long productId) {
    return catalogExpansionAdminService.images(productId);
  }

  @Transactional(readOnly = true)
  public OptionGroupListView optionGroups(long productId) {
    return catalogExpansionAdminService.optionGroups(productId);
  }

  @Transactional(readOnly = true)
  public SkuOptionValuesView skuOptionValues(long productId, long skuId) {
    return catalogExpansionAdminService.skuOptionValues(productId, skuId);
  }

  @Transactional(readOnly = true)
  public FacetDefinitionListView facetDefinitions() {
    return catalogExpansionAdminService.facetDefinitions();
  }

  @Transactional(readOnly = true)
  public FacetDefinitionView facetDefinition(long definitionId) {
    return catalogExpansionAdminService.facetDefinition(definitionId);
  }

  @Transactional(readOnly = true)
  public ProductFacetValuesView productFacetValues(long productId) {
    return catalogExpansionAdminService.productFacetValues(productId);
  }

  @Transactional(readOnly = true)
  public CategoryFacetListView categoryFacets(long categoryId) {
    return catalogExpansionAdminService.categoryFacets(categoryId);
  }

  @Transactional(readOnly = true)
  public DetailSectionListView detailSections(Long productId) {
    return productDetailSectionService.list(productId);
  }
}
