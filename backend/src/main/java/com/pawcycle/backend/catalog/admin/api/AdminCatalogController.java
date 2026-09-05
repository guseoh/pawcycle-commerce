package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogMutationService;
import com.pawcycle.backend.catalog.admin.application.CatalogExpansionAdminService;
import com.pawcycle.backend.catalog.admin.application.ProductDetailSectionService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
  private final AdminCatalogMutationService audits;
  private final CatalogExpansionAdminService catalogExpansionAdminService;
  private final ProductDetailSectionService productDetailSectionService;

  @GetMapping("/brands")
  BrandListResponse brands() {
    return adminCatalogService.brands();
  }

  @PostMapping("/brands")
  ResponseEntity<BrandResponse> createBrand(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody BrandCreateRequest request) {
    BrandResponse brand = audits.execute(principal.memberId(), "CATALOG_BRAND_CREATE", "BRAND", () -> adminCatalogService.createBrand(request), BrandResponse::brandId);
    return ResponseEntity.created(URI.create("/api/admin/brands/" + brand.brandId())).body(brand);
  }

  @GetMapping("/brands/{brandId}")
  BrandResponse brand(@PathVariable long brandId) {
    return catalogExpansionAdminService.brand(brandId);
  }

  @PatchMapping("/brands/{brandId}")
  BrandResponse updateBrand(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long brandId,
      @RequestBody BrandPatchRequest request) {
    BrandResponse brand = audits.execute(principal.memberId(), "CATALOG_BRAND_UPDATE", "BRAND", () -> catalogExpansionAdminService.updateBrand(brandId, request), ignored -> brandId);
    return brand;
  }

  @GetMapping("/categories")
  CategoryListResponse categories() {
    return adminCatalogService.categories();
  }

  @PostMapping("/categories")
  ResponseEntity<CategoryResponse> createCategory(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody CategoryCreateRequest request) {
    CategoryResponse category = audits.execute(principal.memberId(), "CATALOG_CATEGORY_CREATE", "CATEGORY", () -> adminCatalogService.createCategory(request), CategoryResponse::categoryId);
    return ResponseEntity.created(URI.create("/api/admin/categories/" + category.categoryId()))
        .body(category);
  }

  @GetMapping("/categories/{categoryId}")
  CategoryResponse category(@PathVariable Long categoryId) {
    return adminCatalogService.category(categoryId);
  }

  @PatchMapping("/categories/{categoryId}")
  CategoryResponse updateCategory(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long categoryId,
      @RequestBody CategoryPatchRequest request) {
    CategoryResponse category = audits.execute(principal.memberId(), "CATALOG_CATEGORY_UPDATE", "CATEGORY", () -> adminCatalogService.updateCategory(categoryId, request), ignored -> categoryId);
    return category;
  }

  @GetMapping("/products")
  ProductListResponse products() {
    return adminCatalogService.products();
  }

  @PostMapping("/products")
  ResponseEntity<ProductResponse> createProduct(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody ProductCreateRequest request) {
    ProductResponse product = audits.execute(principal.memberId(), "CATALOG_PRODUCT_CREATE", "PRODUCT", () -> adminCatalogService.createProduct(request), ProductResponse::productId);
    return ResponseEntity.created(URI.create("/api/admin/products/" + product.productId()))
        .body(product);
  }

  @GetMapping("/products/{productId}")
  ProductResponse product(@PathVariable Long productId) {
    return adminCatalogService.product(productId);
  }

  @PatchMapping("/products/{productId}")
  ProductResponse updateProduct(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @RequestBody ProductPatchRequest request) {
    ProductResponse product = audits.execute(principal.memberId(), "CATALOG_PRODUCT_UPDATE", "PRODUCT", () -> adminCatalogService.updateProduct(productId, request), ignored -> productId);
    return product;
  }

  @GetMapping("/products/{productId}/skus")
  SkuListResponse skus(@PathVariable Long productId) {
    return adminCatalogService.skus(productId);
  }

  @PostMapping("/products/{productId}/skus")
  ResponseEntity<SkuResponse> createSku(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @Valid @RequestBody SkuCreateRequest request) {
    SkuResponse sku = audits.execute(principal.memberId(), "CATALOG_SKU_CREATE", "SKU", () -> adminCatalogService.createSku(productId, request), SkuResponse::skuId);
    return ResponseEntity.created(
            URI.create("/api/admin/products/" + productId + "/skus/" + sku.skuId()))
        .body(sku);
  }

  @PatchMapping("/products/{productId}/skus/{skuId}")
  SkuResponse updateSku(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @PathVariable Long skuId,
      @RequestBody SkuPatchRequest request) {
    SkuResponse sku = audits.execute(principal.memberId(), "CATALOG_SKU_UPDATE", "SKU", () -> adminCatalogService.updateSku(productId, skuId, request), ignored -> skuId);
    return sku;
  }

  @GetMapping("/products/{productId}/images")
  ImageListResponse images(@PathVariable long productId) {
    return catalogExpansionAdminService.images(productId);
  }

  @PostMapping("/products/{productId}/images")
  ResponseEntity<ImageResponse> createImage(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @Valid @RequestBody ImageCreateRequest request) {
    ImageResponse image = audits.execute(principal.memberId(), "CATALOG_PRODUCT_IMAGE_CREATE", "PRODUCT_IMAGE", () -> catalogExpansionAdminService.createImage(productId, request), ImageResponse::imageId);
    return ResponseEntity.created(
            URI.create("/api/admin/products/" + productId + "/images/" + image.imageId()))
        .body(image);
  }

  @PatchMapping("/products/{productId}/images/{imageId}")
  ImageResponse updateImage(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long imageId,
      @RequestBody ImagePatchRequest request) {
    ImageResponse image = audits.execute(principal.memberId(), "CATALOG_PRODUCT_IMAGE_UPDATE", "PRODUCT_IMAGE", () -> catalogExpansionAdminService.updateImage(productId, imageId, request), ignored -> imageId);
    return image;
  }

  @DeleteMapping("/products/{productId}/images/{imageId}")
  void deleteImage(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long imageId) {
    audits.execute(principal.memberId(), "CATALOG_PRODUCT_IMAGE_DELETE", "PRODUCT_IMAGE", imageId, () -> catalogExpansionAdminService.deleteImage(productId, imageId));
  }

  @GetMapping("/products/{productId}/option-groups")
  OptionGroupListResponse optionGroups(@PathVariable long productId) {
    return catalogExpansionAdminService.optionGroups(productId);
  }

  @PostMapping("/products/{productId}/option-groups")
  ResponseEntity<OptionGroupResponse> createOptionGroup(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @Valid @RequestBody OptionGroupCreateRequest request) {
    OptionGroupResponse group = audits.execute(principal.memberId(), "CATALOG_OPTION_GROUP_CREATE", "PRODUCT_OPTION_GROUP", () -> catalogExpansionAdminService.createOptionGroup(productId, request), OptionGroupResponse::optionGroupId);
    return ResponseEntity.created(
            URI.create(
                "/api/admin/products/" + productId + "/option-groups/" + group.optionGroupId()))
        .body(group);
  }

  @PatchMapping("/products/{productId}/option-groups/{groupId}")
  OptionGroupResponse updateOptionGroup(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId,
      @RequestBody OptionGroupPatchRequest request) {
    OptionGroupResponse group = audits.execute(principal.memberId(), "CATALOG_OPTION_GROUP_UPDATE", "PRODUCT_OPTION_GROUP", () -> catalogExpansionAdminService.updateOptionGroup(productId, groupId, request), ignored -> groupId);
    return group;
  }

  @DeleteMapping("/products/{productId}/option-groups/{groupId}")
  void deleteOptionGroup(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId) {
    audits.execute(principal.memberId(), "CATALOG_OPTION_GROUP_DELETE", "PRODUCT_OPTION_GROUP", groupId, () -> catalogExpansionAdminService.deleteOptionGroup(productId, groupId));
  }

  @PostMapping("/products/{productId}/option-groups/{groupId}/values")
  ResponseEntity<OptionValueResponse> createOptionValue(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId,
      @Valid @RequestBody OptionValueCreateRequest request) {
    OptionValueResponse value = audits.execute(principal.memberId(), "CATALOG_OPTION_VALUE_CREATE", "PRODUCT_OPTION_VALUE", () -> catalogExpansionAdminService.createOptionValue(productId, groupId, request), OptionValueResponse::optionValueId);
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
  OptionValueResponse updateOptionValue(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId,
      @PathVariable long valueId,
      @RequestBody OptionValuePatchRequest request) {
    OptionValueResponse value = audits.execute(principal.memberId(), "CATALOG_OPTION_VALUE_UPDATE", "PRODUCT_OPTION_VALUE", () -> catalogExpansionAdminService.updateOptionValue(productId, groupId, valueId, request), ignored -> valueId);
    return value;
  }

  @DeleteMapping("/products/{productId}/option-groups/{groupId}/values/{valueId}")
  void deleteOptionValue(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId,
      @PathVariable long valueId) {
    audits.execute(principal.memberId(), "CATALOG_OPTION_VALUE_DELETE", "PRODUCT_OPTION_VALUE", valueId, () -> catalogExpansionAdminService.deleteOptionValue(productId, groupId, valueId));
  }

  @PutMapping("/products/{productId}/skus/{skuId}/option-values")
  SkuOptionValuesResponse setSkuOptionValues(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long skuId,
      @Valid @RequestBody SkuOptionValuesRequest request) {
    SkuOptionValuesResponse values = audits.execute(principal.memberId(), "CATALOG_SKU_OPTION_VALUES_SET", "SKU", () -> catalogExpansionAdminService.setSkuOptionValues(productId, skuId, request), ignored -> skuId);
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
  ResponseEntity<FacetDefinitionResponse> createFacetDefinition(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody FacetDefinitionCreateRequest request) {
    FacetDefinitionResponse definition = audits.execute(principal.memberId(), "CATALOG_FACET_CREATE", "FACET_DEFINITION", () -> catalogExpansionAdminService.createFacetDefinition(request), FacetDefinitionResponse::facetDefinitionId);
    return ResponseEntity.created(URI.create("/api/admin/facets/" + definition.facetDefinitionId()))
        .body(definition);
  }

  @PatchMapping("/facets/{definitionId}")
  FacetDefinitionResponse updateFacetDefinition(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @RequestBody FacetDefinitionPatchRequest request) {
    FacetDefinitionResponse definition = audits.execute(principal.memberId(), "CATALOG_FACET_UPDATE", "FACET_DEFINITION", () -> catalogExpansionAdminService.updateFacetDefinition(definitionId, request), ignored -> definitionId);
    return definition;
  }

  @DeleteMapping("/facets/{definitionId}")
  void deleteFacetDefinition(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId) {
    audits.execute(principal.memberId(), "CATALOG_FACET_DELETE", "FACET_DEFINITION", definitionId, () -> catalogExpansionAdminService.deleteFacetDefinition(definitionId));
  }

  @PostMapping("/facets/{definitionId}/options")
  ResponseEntity<FacetOptionResponse> createFacetOption(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @Valid @RequestBody FacetOptionCreateRequest request) {
    FacetOptionResponse option = audits.execute(principal.memberId(), "CATALOG_FACET_OPTION_CREATE", "FACET_OPTION", () -> catalogExpansionAdminService.createFacetOption(definitionId, request), FacetOptionResponse::facetOptionId);
    return ResponseEntity.created(
            URI.create("/api/admin/facets/" + definitionId + "/options/" + option.facetOptionId()))
        .body(option);
  }

  @PatchMapping("/facets/{definitionId}/options/{optionId}")
  FacetOptionResponse updateFacetOption(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @PathVariable long optionId,
      @RequestBody FacetOptionPatchRequest request) {
    FacetOptionResponse option = audits.execute(principal.memberId(), "CATALOG_FACET_OPTION_UPDATE", "FACET_OPTION", () -> catalogExpansionAdminService.updateFacetOption(definitionId, optionId, request), ignored -> optionId);
    return option;
  }

  @DeleteMapping("/facets/{definitionId}/options/{optionId}")
  void deleteFacetOption(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @PathVariable long optionId) {
    audits.execute(principal.memberId(), "CATALOG_FACET_OPTION_DELETE", "FACET_OPTION", optionId, () -> catalogExpansionAdminService.deleteFacetOption(definitionId, optionId));
  }

  @PutMapping("/categories/{categoryId}/facets/{definitionId}")
  CategoryFacetResponse assignCategoryFacet(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long categoryId,
      @PathVariable long definitionId,
      @Valid @RequestBody CategoryFacetAssignRequest request) {
    CategoryFacetResponse facet = audits.execute(principal.memberId(), "CATALOG_CATEGORY_FACET_SET", "CATEGORY", () -> catalogExpansionAdminService.assignCategoryFacet(categoryId, definitionId, request), ignored -> categoryId);
    return facet;
  }

  @DeleteMapping("/categories/{categoryId}/facets/{definitionId}")
  void removeCategoryFacet(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long categoryId,
      @PathVariable long definitionId) {
    audits.execute(principal.memberId(), "CATALOG_CATEGORY_FACET_DELETE", "CATEGORY", categoryId, () -> catalogExpansionAdminService.removeCategoryFacet(categoryId, definitionId));
  }

  @PutMapping("/products/{productId}/facet-values")
  ProductFacetValuesResponse setProductFacetValues(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @Valid @RequestBody ProductFacetValuesRequest request) {
    ProductFacetValuesResponse values = audits.execute(principal.memberId(), "CATALOG_PRODUCT_FACET_VALUES_SET", "PRODUCT", () -> catalogExpansionAdminService.setProductFacetValues(productId, request), ignored -> productId);
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
  ResponseEntity<DetailSectionResponse> createDetailSection(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @Valid @RequestBody DetailSectionCreateRequest request) {
    DetailSectionResponse section = audits.execute(principal.memberId(), "CATALOG_DETAIL_SECTION_CREATE", "PRODUCT_DETAIL_SECTION", () -> productDetailSectionService.create(productId, request), DetailSectionResponse::sectionId);
    return ResponseEntity.created(
            URI.create(
                "/api/admin/products/" + productId + "/detail-sections/" + section.sectionId()))
        .body(section);
  }

  @PatchMapping("/products/{productId}/detail-sections/{sectionId}")
  DetailSectionResponse updateDetailSection(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @PathVariable Long sectionId,
      @RequestBody DetailSectionPatchRequest request) {
    DetailSectionResponse section = audits.execute(principal.memberId(), "CATALOG_DETAIL_SECTION_UPDATE", "PRODUCT_DETAIL_SECTION", () -> productDetailSectionService.update(productId, sectionId, request), ignored -> sectionId);
    return section;
  }

  @DeleteMapping("/products/{productId}/detail-sections/{sectionId}")
  void deleteDetailSection(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @PathVariable Long sectionId) {
    audits.execute(principal.memberId(), "CATALOG_DETAIL_SECTION_DELETE", "PRODUCT_DETAIL_SECTION", sectionId, () -> productDetailSectionService.delete(productId, sectionId));
  }
}
