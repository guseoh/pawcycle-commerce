package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogMutationService;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
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
  private final AdminCatalogMutationService mutations;

  @GetMapping("/brands")
  BrandListResponse brands() {
    return adminCatalogService.brands();
  }

  @PostMapping("/brands")
  ResponseEntity<BrandResponse> createBrand(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody BrandCreateRequest request) {
    BrandResponse brand = mutations.createBrand(principal.memberId(), request);
    return ResponseEntity.created(URI.create("/api/admin/brands/" + brand.brandId())).body(brand);
  }

  @GetMapping("/brands/{brandId}")
  BrandResponse brand(@PathVariable long brandId) {
    return mutations.brand(brandId);
  }

  @PatchMapping("/brands/{brandId}")
  BrandResponse updateBrand(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long brandId,
      @RequestBody BrandPatchRequest request) {
    BrandResponse brand = mutations.updateBrand(principal.memberId(), brandId, request);
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
    CategoryResponse category = mutations.createCategory(principal.memberId(), request);
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
    CategoryResponse category = mutations.updateCategory(principal.memberId(), categoryId, request);
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
    ProductResponse product = mutations.createProduct(principal.memberId(), request);
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
    ProductResponse product = mutations.updateProduct(principal.memberId(), productId, request);
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
    SkuResponse sku = mutations.createSku(principal.memberId(), productId, request);
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
    SkuResponse sku = mutations.updateSku(principal.memberId(), productId, skuId, request);
    return sku;
  }

  @GetMapping("/products/{productId}/images")
  ImageListResponse images(@PathVariable long productId) {
    return mutations.images(productId);
  }

  @PostMapping("/products/{productId}/images")
  ResponseEntity<ImageResponse> createImage(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @Valid @RequestBody ImageCreateRequest request) {
    ImageResponse image = mutations.createImage(principal.memberId(), productId, request);
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
    ImageResponse image = mutations.updateImage(principal.memberId(), productId, imageId, request);
    return image;
  }

  @DeleteMapping("/products/{productId}/images/{imageId}")
  void deleteImage(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long imageId) {
    mutations.deleteImage(principal.memberId(), productId, imageId);
  }

  @GetMapping("/products/{productId}/option-groups")
  OptionGroupListResponse optionGroups(@PathVariable long productId) {
    return mutations.optionGroups(productId);
  }

  @PostMapping("/products/{productId}/option-groups")
  ResponseEntity<OptionGroupResponse> createOptionGroup(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @Valid @RequestBody OptionGroupCreateRequest request) {
    OptionGroupResponse group =
        mutations.createOptionGroup(principal.memberId(), productId, request);
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
    OptionGroupResponse group =
        mutations.updateOptionGroup(principal.memberId(), productId, groupId, request);
    return group;
  }

  @DeleteMapping("/products/{productId}/option-groups/{groupId}")
  void deleteOptionGroup(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId) {
    mutations.deleteOptionGroup(principal.memberId(), productId, groupId);
  }

  @PostMapping("/products/{productId}/option-groups/{groupId}/values")
  ResponseEntity<OptionValueResponse> createOptionValue(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId,
      @Valid @RequestBody OptionValueCreateRequest request) {
    OptionValueResponse value =
        mutations.createOptionValue(principal.memberId(), productId, groupId, request);
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
    OptionValueResponse value =
        mutations.updateOptionValue(principal.memberId(), productId, groupId, valueId, request);
    return value;
  }

  @DeleteMapping("/products/{productId}/option-groups/{groupId}/values/{valueId}")
  void deleteOptionValue(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long groupId,
      @PathVariable long valueId) {
    mutations.deleteOptionValue(principal.memberId(), productId, groupId, valueId);
  }

  @PutMapping("/products/{productId}/skus/{skuId}/option-values")
  SkuOptionValuesResponse setSkuOptionValues(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @PathVariable long skuId,
      @Valid @RequestBody SkuOptionValuesRequest request) {
    SkuOptionValuesResponse values =
        mutations.setSkuOptionValues(principal.memberId(), productId, skuId, request);
    return values;
  }

  @GetMapping("/products/{productId}/skus/{skuId}/option-values")
  SkuOptionValuesResponse skuOptionValues(@PathVariable long productId, @PathVariable long skuId) {
    return mutations.skuOptionValues(productId, skuId);
  }

  @GetMapping("/facets")
  FacetDefinitionListResponse facetDefinitions() {
    return mutations.facetDefinitions();
  }

  @GetMapping("/facets/{definitionId}")
  FacetDefinitionResponse facetDefinition(@PathVariable long definitionId) {
    return mutations.facetDefinition(definitionId);
  }

  @PostMapping("/facets")
  ResponseEntity<FacetDefinitionResponse> createFacetDefinition(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody FacetDefinitionCreateRequest request) {
    FacetDefinitionResponse definition =
        mutations.createFacetDefinition(principal.memberId(), request);
    return ResponseEntity.created(URI.create("/api/admin/facets/" + definition.facetDefinitionId()))
        .body(definition);
  }

  @PatchMapping("/facets/{definitionId}")
  FacetDefinitionResponse updateFacetDefinition(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @RequestBody FacetDefinitionPatchRequest request) {
    FacetDefinitionResponse definition =
        mutations.updateFacetDefinition(principal.memberId(), definitionId, request);
    return definition;
  }

  @DeleteMapping("/facets/{definitionId}")
  void deleteFacetDefinition(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId) {
    mutations.deleteFacetDefinition(principal.memberId(), definitionId);
  }

  @PostMapping("/facets/{definitionId}/options")
  ResponseEntity<FacetOptionResponse> createFacetOption(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @Valid @RequestBody FacetOptionCreateRequest request) {
    FacetOptionResponse option =
        mutations.createFacetOption(principal.memberId(), definitionId, request);
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
    FacetOptionResponse option =
        mutations.updateFacetOption(principal.memberId(), definitionId, optionId, request);
    return option;
  }

  @DeleteMapping("/facets/{definitionId}/options/{optionId}")
  void deleteFacetOption(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long definitionId,
      @PathVariable long optionId) {
    mutations.deleteFacetOption(principal.memberId(), definitionId, optionId);
  }

  @PutMapping("/categories/{categoryId}/facets/{definitionId}")
  CategoryFacetResponse assignCategoryFacet(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long categoryId,
      @PathVariable long definitionId,
      @Valid @RequestBody CategoryFacetAssignRequest request) {
    CategoryFacetResponse facet =
        mutations.assignCategoryFacet(principal.memberId(), categoryId, definitionId, request);
    return facet;
  }

  @DeleteMapping("/categories/{categoryId}/facets/{definitionId}")
  void removeCategoryFacet(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long categoryId,
      @PathVariable long definitionId) {
    mutations.removeCategoryFacet(principal.memberId(), categoryId, definitionId);
  }

  @PutMapping("/products/{productId}/facet-values")
  ProductFacetValuesResponse setProductFacetValues(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long productId,
      @Valid @RequestBody ProductFacetValuesRequest request) {
    ProductFacetValuesResponse values =
        mutations.setProductFacetValues(principal.memberId(), productId, request);
    return values;
  }

  @GetMapping("/products/{productId}/facet-values")
  ProductFacetValuesResponse productFacetValues(@PathVariable long productId) {
    return mutations.productFacetValues(productId);
  }

  @GetMapping("/categories/{categoryId}/facets")
  CategoryFacetListResponse categoryFacets(@PathVariable long categoryId) {
    return mutations.categoryFacets(categoryId);
  }

  @GetMapping("/products/{productId}/detail-sections")
  DetailSectionListResponse detailSections(@PathVariable Long productId) {
    return mutations.detailSections(productId);
  }

  @PostMapping("/products/{productId}/detail-sections")
  ResponseEntity<DetailSectionResponse> createDetailSection(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @Valid @RequestBody DetailSectionCreateRequest request) {
    DetailSectionResponse section =
        mutations.createDetailSection(principal.memberId(), productId, request);
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
    DetailSectionResponse section =
        mutations.updateDetailSection(principal.memberId(), productId, sectionId, request);
    return section;
  }

  @DeleteMapping("/products/{productId}/detail-sections/{sectionId}")
  void deleteDetailSection(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable Long productId,
      @PathVariable Long sectionId) {
    mutations.deleteDetailSection(principal.memberId(), productId, sectionId);
  }
}
