package com.pawcycle.backend.catalog.admin.application;

import static com.pawcycle.backend.catalog.admin.application.CatalogAdminMapping.command;
import static com.pawcycle.backend.catalog.admin.application.CatalogAdminMapping.response;

import com.pawcycle.backend.catalog.admin.api.BrandCreateRequest;
import com.pawcycle.backend.catalog.admin.api.BrandPatchRequest;
import com.pawcycle.backend.catalog.admin.api.BrandResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryCreateRequest;
import com.pawcycle.backend.catalog.admin.api.CategoryFacetAssignRequest;
import com.pawcycle.backend.catalog.admin.api.CategoryFacetListResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryFacetResponse;
import com.pawcycle.backend.catalog.admin.api.CategoryPatchRequest;
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
import com.pawcycle.backend.catalog.admin.api.ProductCreateRequest;
import com.pawcycle.backend.catalog.admin.api.ProductFacetValuesRequest;
import com.pawcycle.backend.catalog.admin.api.ProductFacetValuesResponse;
import com.pawcycle.backend.catalog.admin.api.ProductPatchRequest;
import com.pawcycle.backend.catalog.admin.api.ProductResponse;
import com.pawcycle.backend.catalog.admin.api.SkuCreateRequest;
import com.pawcycle.backend.catalog.admin.api.SkuOptionValuesRequest;
import com.pawcycle.backend.catalog.admin.api.SkuOptionValuesResponse;
import com.pawcycle.backend.catalog.admin.api.SkuPatchRequest;
import com.pawcycle.backend.catalog.admin.api.SkuResponse;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminPersistence;
import com.pawcycle.backend.catalog.admin.persistence.ProductDetailSectionPersistence;
import com.pawcycle.backend.commerce.AdminAuditService;
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
  public BrandResponse updateBrand(long adminId, long brandId, BrandPatchRequest request) {
    return execute(
        adminId,
        "CATALOG_BRAND_UPDATE",
        "BRAND",
        () -> response(catalogExpansionAdminService.updateBrand(brandId, command(request))),
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
  public ImageResponse createImage(long adminId, long productId, ImageCreateRequest request) {
    return execute(
        adminId,
        "CATALOG_PRODUCT_IMAGE_CREATE",
        "PRODUCT_IMAGE",
        () -> response(catalogExpansionAdminService.createImage(productId, command(request))),
        ImageResponse::imageId);
  }

  @Transactional
  public ImageResponse updateImage(
      long adminId, long productId, long imageId, ImagePatchRequest request) {
    return execute(
        adminId,
        "CATALOG_PRODUCT_IMAGE_UPDATE",
        "PRODUCT_IMAGE",
        () ->
            response(
                catalogExpansionAdminService.updateImage(productId, imageId, command(request))),
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
  public OptionGroupResponse createOptionGroup(
      long adminId, long productId, OptionGroupCreateRequest request) {
    return execute(
        adminId,
        "CATALOG_OPTION_GROUP_CREATE",
        "PRODUCT_OPTION_GROUP",
        () -> response(catalogExpansionAdminService.createOptionGroup(productId, command(request))),
        OptionGroupResponse::optionGroupId);
  }

  @Transactional
  public OptionGroupResponse updateOptionGroup(
      long adminId, long productId, long groupId, OptionGroupPatchRequest request) {
    return execute(
        adminId,
        "CATALOG_OPTION_GROUP_UPDATE",
        "PRODUCT_OPTION_GROUP",
        () ->
            response(
                catalogExpansionAdminService.updateOptionGroup(
                    productId, groupId, command(request))),
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
  public OptionValueResponse createOptionValue(
      long adminId, long productId, long groupId, OptionValueCreateRequest request) {
    return execute(
        adminId,
        "CATALOG_OPTION_VALUE_CREATE",
        "PRODUCT_OPTION_VALUE",
        () ->
            response(
                catalogExpansionAdminService.createOptionValue(
                    productId, groupId, command(request))),
        OptionValueResponse::optionValueId);
  }

  @Transactional
  public OptionValueResponse updateOptionValue(
      long adminId, long productId, long groupId, long valueId, OptionValuePatchRequest request) {
    return execute(
        adminId,
        "CATALOG_OPTION_VALUE_UPDATE",
        "PRODUCT_OPTION_VALUE",
        () ->
            response(
                catalogExpansionAdminService.updateOptionValue(
                    productId, groupId, valueId, command(request))),
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
  public SkuOptionValuesResponse setSkuOptionValues(
      long adminId, long productId, long skuId, SkuOptionValuesRequest request) {
    return execute(
        adminId,
        "CATALOG_SKU_OPTION_VALUES_SET",
        "SKU",
        () ->
            response(
                catalogExpansionAdminService.setSkuOptionValues(
                    productId, skuId, command(request))),
        ignored -> skuId);
  }

  @Transactional
  public FacetDefinitionResponse createFacetDefinition(
      long adminId, FacetDefinitionCreateRequest request) {
    return execute(
        adminId,
        "CATALOG_FACET_CREATE",
        "FACET_DEFINITION",
        () -> response(catalogExpansionAdminService.createFacetDefinition(command(request))),
        FacetDefinitionResponse::facetDefinitionId);
  }

  @Transactional
  public FacetDefinitionResponse updateFacetDefinition(
      long adminId, long definitionId, FacetDefinitionPatchRequest request) {
    return execute(
        adminId,
        "CATALOG_FACET_UPDATE",
        "FACET_DEFINITION",
        () ->
            response(
                catalogExpansionAdminService.updateFacetDefinition(definitionId, command(request))),
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
  public FacetOptionResponse createFacetOption(
      long adminId, long definitionId, FacetOptionCreateRequest request) {
    return execute(
        adminId,
        "CATALOG_FACET_OPTION_CREATE",
        "FACET_OPTION",
        () ->
            response(
                catalogExpansionAdminService.createFacetOption(definitionId, command(request))),
        FacetOptionResponse::facetOptionId);
  }

  @Transactional
  public FacetOptionResponse updateFacetOption(
      long adminId, long definitionId, long optionId, FacetOptionPatchRequest request) {
    return execute(
        adminId,
        "CATALOG_FACET_OPTION_UPDATE",
        "FACET_OPTION",
        () ->
            response(
                catalogExpansionAdminService.updateFacetOption(
                    definitionId, optionId, command(request))),
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
  public CategoryFacetResponse assignCategoryFacet(
      long adminId, long categoryId, long definitionId, CategoryFacetAssignRequest request) {
    return execute(
        adminId,
        "CATALOG_CATEGORY_FACET_SET",
        "CATEGORY",
        () ->
            response(
                catalogExpansionAdminService.assignCategoryFacet(
                    categoryId, definitionId, command(request))),
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
  public ProductFacetValuesResponse setProductFacetValues(
      long adminId, long productId, ProductFacetValuesRequest request) {
    return execute(
        adminId,
        "CATALOG_PRODUCT_FACET_VALUES_SET",
        "PRODUCT",
        () ->
            response(
                catalogExpansionAdminService.setProductFacetValues(productId, command(request))),
        ignored -> productId);
  }

  @Transactional
  public DetailSectionResponse createDetailSection(
      long adminId, Long productId, DetailSectionCreateRequest request) {
    return execute(
        adminId,
        "CATALOG_DETAIL_SECTION_CREATE",
        "PRODUCT_DETAIL_SECTION",
        () -> response(productDetailSectionService.create(productId, command(request))),
        DetailSectionResponse::sectionId);
  }

  @Transactional
  public DetailSectionResponse updateDetailSection(
      long adminId, Long productId, Long sectionId, DetailSectionPatchRequest request) {
    return execute(
        adminId,
        "CATALOG_DETAIL_SECTION_UPDATE",
        "PRODUCT_DETAIL_SECTION",
        () -> response(productDetailSectionService.update(productId, sectionId, command(request))),
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
  public BrandResponse brand(long brandId) {
    return response(catalogExpansionAdminService.brand(brandId));
  }

  @Transactional(readOnly = true)
  public ImageListResponse images(long productId) {
    return response(catalogExpansionAdminService.images(productId));
  }

  @Transactional(readOnly = true)
  public OptionGroupListResponse optionGroups(long productId) {
    return response(catalogExpansionAdminService.optionGroups(productId));
  }

  @Transactional(readOnly = true)
  public SkuOptionValuesResponse skuOptionValues(long productId, long skuId) {
    return response(catalogExpansionAdminService.skuOptionValues(productId, skuId));
  }

  @Transactional(readOnly = true)
  public FacetDefinitionListResponse facetDefinitions() {
    return response(catalogExpansionAdminService.facetDefinitions());
  }

  @Transactional(readOnly = true)
  public FacetDefinitionResponse facetDefinition(long definitionId) {
    return response(catalogExpansionAdminService.facetDefinition(definitionId));
  }

  @Transactional(readOnly = true)
  public ProductFacetValuesResponse productFacetValues(long productId) {
    return response(catalogExpansionAdminService.productFacetValues(productId));
  }

  @Transactional(readOnly = true)
  public CategoryFacetListResponse categoryFacets(long categoryId) {
    return response(catalogExpansionAdminService.categoryFacets(categoryId));
  }

  @Transactional(readOnly = true)
  public DetailSectionListResponse detailSections(Long productId) {
    return response(productDetailSectionService.list(productId));
  }
}
