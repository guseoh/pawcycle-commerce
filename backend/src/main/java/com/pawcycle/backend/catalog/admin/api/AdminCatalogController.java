package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
import com.pawcycle.backend.catalog.admin.application.CatalogExpansionAdminService;
import com.pawcycle.backend.catalog.admin.application.ProductDetailSectionService;
import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCatalogController {
  private final AdminCatalogService adminCatalogService;
  private final CatalogExpansionAdminService catalogExpansionAdminService;
  private final ProductDetailSectionService productDetailSectionService;
  private final AdminAuditService audits;

  @GetMapping("/brands")
  BrandListResponse brands() {
    return adminCatalogService.brands();
  }

  @PostMapping("/brands")
  @Transactional
  ResponseEntity<BrandResponse> createBrand(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody BrandCreateRequest request) {
    BrandResponse brand = adminCatalogService.createBrand(request);
    audits.append(principal.memberId(), "CATALOG_BRAND_CREATE", "BRAND", brand.brandId());
    return ResponseEntity.created(URI.create("/api/admin/brands/" + brand.brandId())).body(brand);
  }

  @GetMapping("/brands/{brandId}")
  BrandResponse brand(@PathVariable long brandId) {
    return catalogExpansionAdminService.brand(brandId);
  }

  @PatchMapping("/brands/{brandId}")
  @Transactional
  BrandResponse updateBrand(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long brandId,
      @RequestBody BrandPatchRequest request) {
    BrandResponse brand = catalogExpansionAdminService.updateBrand(brandId, request);
    audits.append(principal.memberId(), "CATALOG_BRAND_UPDATE", "BRAND", brandId);
    return brand;
  }

  @GetMapping("/categories")
  CategoryListResponse categories() {
    return adminCatalogService.categories();
  }

  @PostMapping("/categories")
  @Transactional
  ResponseEntity<CategoryResponse> createCategory(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody CategoryCreateRequest request) {
    CategoryResponse category = adminCatalogService.createCategory(request);
    audits.append(
        principal.memberId(), "CATALOG_CATEGORY_CREATE", "CATEGORY", category.categoryId());
    return ResponseEntity.created(URI.create("/api/admin/categories/" + category.categoryId()))
        .body(category);
  }

  @GetMapping("/categories/{categoryId}")
  CategoryResponse category(@PathVariable Long categoryId) {
    return adminCatalogService.category(categoryId);
  }

  @PatchMapping("/categories/{categoryId}")
  @Transactional
  CategoryResponse updateCategory(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long categoryId,
      @RequestBody CategoryPatchRequest request) {
    CategoryResponse category = adminCatalogService.updateCategory(categoryId, request);
    audits.append(principal.memberId(), "CATALOG_CATEGORY_UPDATE", "CATEGORY", categoryId);
    return category;
  }

  @GetMapping("/products")
  ProductListResponse products() {
    return adminCatalogService.products();
  }

  @PostMapping("/products")
  @Transactional
  ResponseEntity<ProductResponse> createProduct(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody ProductCreateRequest request) {
    ProductResponse product = adminCatalogService.createProduct(request);
    audits.append(principal.memberId(), "CATALOG_PRODUCT_CREATE", "PRODUCT", product.productId());
    return ResponseEntity.created(URI.create("/api/admin/products/" + product.productId()))
        .body(product);
  }

  @GetMapping("/products/{productId}")
  ProductResponse product(@PathVariable Long productId) {
    return adminCatalogService.product(productId);
  }

  @PatchMapping("/products/{productId}")
  @Transactional
  ProductResponse updateProduct(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @RequestBody ProductPatchRequest request) {
    ProductResponse product = adminCatalogService.updateProduct(productId, request);
    audits.append(principal.memberId(), "CATALOG_PRODUCT_UPDATE", "PRODUCT", productId);
    return product;
  }

  @GetMapping("/products/{productId}/skus")
  SkuListResponse skus(@PathVariable Long productId) {
    return adminCatalogService.skus(productId);
  }

  @PostMapping("/products/{productId}/skus")
  @Transactional
  ResponseEntity<SkuResponse> createSku(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @Valid @RequestBody SkuCreateRequest request) {
    SkuResponse sku = adminCatalogService.createSku(productId, request);
    audits.append(principal.memberId(), "CATALOG_SKU_CREATE", "SKU", sku.skuId());
    return ResponseEntity.created(
            URI.create("/api/admin/products/" + productId + "/skus/" + sku.skuId()))
        .body(sku);
  }

  @PatchMapping("/products/{productId}/skus/{skuId}")
  @Transactional
  SkuResponse updateSku(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @PathVariable Long skuId,
      @RequestBody SkuPatchRequest request) {
    SkuResponse sku = adminCatalogService.updateSku(productId, skuId, request);
    audits.append(principal.memberId(), "CATALOG_SKU_UPDATE", "SKU", skuId);
    return sku;
  }

  @GetMapping("/products/{productId}/images")
  ImageListResponse images(@PathVariable long productId) {
    return catalogExpansionAdminService.images(productId);
  }

  @PostMapping("/products/{productId}/images")
  @Transactional
  ResponseEntity<ImageResponse> createImage(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @Valid @RequestBody ImageCreateRequest request) {
    ImageResponse image = catalogExpansionAdminService.createImage(productId, request);
    audits.append(
        principal.memberId(), "CATALOG_PRODUCT_IMAGE_CREATE", "PRODUCT_IMAGE", image.imageId());
    return ResponseEntity.created(
            URI.create("/api/admin/products/" + productId + "/images/" + image.imageId()))
        .body(image);
  }

  @PatchMapping("/products/{productId}/images/{imageId}")
  @Transactional
  ImageResponse updateImage(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long imageId,
      @RequestBody ImagePatchRequest request) {
    ImageResponse image =
        catalogExpansionAdminService.updateImage(productId, imageId, request);
    audits.append(principal.memberId(), "CATALOG_PRODUCT_IMAGE_UPDATE", "PRODUCT_IMAGE", imageId);
    return image;
  }

  @DeleteMapping("/products/{productId}/images/{imageId}")
  @Transactional
  void deleteImage(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long imageId) {
    catalogExpansionAdminService.deleteImage(productId, imageId);
    audits.append(principal.memberId(), "CATALOG_PRODUCT_IMAGE_DELETE", "PRODUCT_IMAGE", imageId);
  }

  @GetMapping("/products/{productId}/option-groups")
  OptionGroupListResponse optionGroups(@PathVariable long productId) {
    return catalogExpansionAdminService.optionGroups(productId);
  }

  @PostMapping("/products/{productId}/option-groups")
  @Transactional
  ResponseEntity<OptionGroupResponse> createOptionGroup(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @Valid @RequestBody OptionGroupCreateRequest request) {
    OptionGroupResponse group =
        catalogExpansionAdminService.createOptionGroup(productId, request);
    audits.append(
        principal.memberId(),
        "CATALOG_OPTION_GROUP_CREATE",
        "PRODUCT_OPTION_GROUP",
        group.optionGroupId());
    return ResponseEntity.created(
            URI.create(
                "/api/admin/products/" + productId + "/option-groups/" + group.optionGroupId()))
        .body(group);
  }

  @PatchMapping("/products/{productId}/option-groups/{groupId}")
  @Transactional
  OptionGroupResponse updateOptionGroup(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId,
      @RequestBody OptionGroupPatchRequest request) {
    OptionGroupResponse group =
        catalogExpansionAdminService.updateOptionGroup(productId, groupId, request);
    audits.append(
        principal.memberId(), "CATALOG_OPTION_GROUP_UPDATE", "PRODUCT_OPTION_GROUP", groupId);
    return group;
  }

  @DeleteMapping("/products/{productId}/option-groups/{groupId}")
  @Transactional
  void deleteOptionGroup(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId) {
    catalogExpansionAdminService.deleteOptionGroup(productId, groupId);
    audits.append(
        principal.memberId(), "CATALOG_OPTION_GROUP_DELETE", "PRODUCT_OPTION_GROUP", groupId);
  }

  @PostMapping("/products/{productId}/option-groups/{groupId}/values")
  @Transactional
  ResponseEntity<OptionValueResponse> createOptionValue(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId,
      @Valid @RequestBody OptionValueCreateRequest request) {
    OptionValueResponse value =
        catalogExpansionAdminService.createOptionValue(productId, groupId, request);
    audits.append(
        principal.memberId(),
        "CATALOG_OPTION_VALUE_CREATE",
        "PRODUCT_OPTION_VALUE",
        value.optionValueId());
    return ResponseEntity.created(
            URI.create(
                "/api/admin/products/"
                    + productId
                    + "/option-groups/"
                    + groupId
                    + "/values/"
                    + value.optionValueId()))
        .body(value);
  }

  @PatchMapping("/products/{productId}/option-groups/{groupId}/values/{valueId}")
  @Transactional
  OptionValueResponse updateOptionValue(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId,
      @PathVariable long valueId,
      @RequestBody OptionValuePatchRequest request) {
    OptionValueResponse value =
        catalogExpansionAdminService.updateOptionValue(productId, groupId, valueId, request);
    audits.append(
        principal.memberId(), "CATALOG_OPTION_VALUE_UPDATE", "PRODUCT_OPTION_VALUE", valueId);
    return value;
  }

  @DeleteMapping("/products/{productId}/option-groups/{groupId}/values/{valueId}")
  @Transactional
  void deleteOptionValue(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId,
      @PathVariable long valueId) {
    catalogExpansionAdminService.deleteOptionValue(productId, groupId, valueId);
    audits.append(
        principal.memberId(), "CATALOG_OPTION_VALUE_DELETE", "PRODUCT_OPTION_VALUE", valueId);
  }

  @PutMapping("/products/{productId}/skus/{skuId}/option-values")
  @Transactional
  SkuOptionValuesResponse setSkuOptionValues(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long skuId,
      @Valid @RequestBody SkuOptionValuesRequest request) {
    SkuOptionValuesResponse values =
        catalogExpansionAdminService.setSkuOptionValues(productId, skuId, request);
    audits.append(principal.memberId(), "CATALOG_SKU_OPTION_VALUES_SET", "SKU", skuId);
    return values;
  }

  @GetMapping("/products/{productId}/skus/{skuId}/option-values")
  SkuOptionValuesResponse skuOptionValues(
      @PathVariable long productId, @PathVariable long skuId) {
    return catalogExpansionAdminService.skuOptionValues(productId, skuId);
  }

  @GetMapping("/facets")
  FacetDefinitionListResponse facetDefinitions() {
    return catalogExpansionAdminService.facetDefinitions();
  }

  @GetMapping("/facets/{definitionId}")
  FacetDefinitionResponse facetDefinition(@PathVariable long definitionId) {
    return catalogExpansionAdminService.facetDefinition(definitionId);
  }

  @PostMapping("/facets")
  @Transactional
  ResponseEntity<FacetDefinitionResponse> createFacetDefinition(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody FacetDefinitionCreateRequest request) {
    FacetDefinitionResponse definition =
        catalogExpansionAdminService.createFacetDefinition(request);
    audits.append(
        principal.memberId(),
        "CATALOG_FACET_CREATE",
        "FACET_DEFINITION",
        definition.facetDefinitionId());
    return ResponseEntity.created(URI.create("/api/admin/facets/" + definition.facetDefinitionId()))
        .body(definition);
  }

  @PatchMapping("/facets/{definitionId}")
  @Transactional
  FacetDefinitionResponse updateFacetDefinition(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @RequestBody FacetDefinitionPatchRequest request) {
    FacetDefinitionResponse definition =
        catalogExpansionAdminService.updateFacetDefinition(definitionId, request);
    audits.append(principal.memberId(), "CATALOG_FACET_UPDATE", "FACET_DEFINITION", definitionId);
    return definition;
  }

  @DeleteMapping("/facets/{definitionId}")
  @Transactional
  void deleteFacetDefinition(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId) {
    catalogExpansionAdminService.deleteFacetDefinition(definitionId);
    audits.append(principal.memberId(), "CATALOG_FACET_DELETE", "FACET_DEFINITION", definitionId);
  }

  @PostMapping("/facets/{definitionId}/options")
  @Transactional
  ResponseEntity<FacetOptionResponse> createFacetOption(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @Valid @RequestBody FacetOptionCreateRequest request) {
    FacetOptionResponse option =
        catalogExpansionAdminService.createFacetOption(definitionId, request);
    audits.append(
        principal.memberId(),
        "CATALOG_FACET_OPTION_CREATE",
        "FACET_OPTION",
        option.facetOptionId());
    return ResponseEntity.created(
            URI.create("/api/admin/facets/" + definitionId + "/options/" + option.facetOptionId()))
        .body(option);
  }

  @PatchMapping("/facets/{definitionId}/options/{optionId}")
  @Transactional
  FacetOptionResponse updateFacetOption(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @PathVariable long optionId,
      @RequestBody FacetOptionPatchRequest request) {
    FacetOptionResponse option =
        catalogExpansionAdminService.updateFacetOption(definitionId, optionId, request);
    audits.append(principal.memberId(), "CATALOG_FACET_OPTION_UPDATE", "FACET_OPTION", optionId);
    return option;
  }

  @DeleteMapping("/facets/{definitionId}/options/{optionId}")
  @Transactional
  void deleteFacetOption(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @PathVariable long optionId) {
    catalogExpansionAdminService.deleteFacetOption(definitionId, optionId);
    audits.append(principal.memberId(), "CATALOG_FACET_OPTION_DELETE", "FACET_OPTION", optionId);
  }

  @PutMapping("/categories/{categoryId}/facets/{definitionId}")
  @Transactional
  CategoryFacetResponse assignCategoryFacet(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long categoryId,
      @PathVariable long definitionId,
      @Valid @RequestBody CategoryFacetAssignRequest request) {
    CategoryFacetResponse facet =
        catalogExpansionAdminService.assignCategoryFacet(categoryId, definitionId, request);
    audits.append(principal.memberId(), "CATALOG_CATEGORY_FACET_SET", "CATEGORY", categoryId);
    return facet;
  }

  @DeleteMapping("/categories/{categoryId}/facets/{definitionId}")
  @Transactional
  void removeCategoryFacet(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long categoryId,
      @PathVariable long definitionId) {
    catalogExpansionAdminService.removeCategoryFacet(categoryId, definitionId);
    audits.append(principal.memberId(), "CATALOG_CATEGORY_FACET_DELETE", "CATEGORY", categoryId);
  }

  @PutMapping("/products/{productId}/facet-values")
  @Transactional
  ProductFacetValuesResponse setProductFacetValues(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @Valid @RequestBody ProductFacetValuesRequest request) {
    ProductFacetValuesResponse values =
        catalogExpansionAdminService.setProductFacetValues(productId, request);
    audits.append(principal.memberId(), "CATALOG_PRODUCT_FACET_VALUES_SET", "PRODUCT", productId);
    return values;
  }

  @GetMapping("/products/{productId}/facet-values")
  ProductFacetValuesResponse productFacetValues(@PathVariable long productId) {
    return catalogExpansionAdminService.productFacetValues(productId);
  }

  @GetMapping("/categories/{categoryId}/facets")
  CategoryFacetListResponse categoryFacets(@PathVariable long categoryId) {
    return catalogExpansionAdminService.categoryFacets(categoryId);
  }

  @GetMapping("/products/{productId}/detail-sections")
  DetailSectionListResponse detailSections(@PathVariable Long productId) {
    return productDetailSectionService.list(productId);
  }

  @PostMapping("/products/{productId}/detail-sections")
  @Transactional
  ResponseEntity<DetailSectionResponse> createDetailSection(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @Valid @RequestBody DetailSectionCreateRequest request) {
    DetailSectionResponse section =
        productDetailSectionService.create(productId, request);
    audits.append(
        principal.memberId(),
        "CATALOG_DETAIL_SECTION_CREATE",
        "PRODUCT_DETAIL_SECTION",
        section.sectionId());
    return ResponseEntity.created(
            URI.create(
                "/api/admin/products/" + productId + "/detail-sections/" + section.sectionId()))
        .body(section);
  }

  @PatchMapping("/products/{productId}/detail-sections/{sectionId}")
  @Transactional
  DetailSectionResponse updateDetailSection(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @PathVariable Long sectionId,
      @RequestBody DetailSectionPatchRequest request) {
    DetailSectionResponse section =
        productDetailSectionService.update(productId, sectionId, request);
    audits.append(
        principal.memberId(), "CATALOG_DETAIL_SECTION_UPDATE", "PRODUCT_DETAIL_SECTION", sectionId);
    return section;
  }

  @DeleteMapping("/products/{productId}/detail-sections/{sectionId}")
  @Transactional
  void deleteDetailSection(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @PathVariable Long sectionId) {
    productDetailSectionService.delete(productId, sectionId);
    audits.append(
        principal.memberId(), "CATALOG_DETAIL_SECTION_DELETE", "PRODUCT_DETAIL_SECTION", sectionId);
  }
}
