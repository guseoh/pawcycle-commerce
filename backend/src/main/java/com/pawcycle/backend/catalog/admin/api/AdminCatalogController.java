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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
	AdminCatalogViews.BrandList brands() { return adminCatalogService.brands(); }

	@PostMapping("/brands")
	@Transactional
	ResponseEntity<AdminCatalogViews.Brand> createBrand(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@Valid @RequestBody AdminCatalogRequests.BrandCreate request) {
		AdminCatalogViews.Brand brand = adminCatalogService.createBrand(request);
		audits.append(principal.memberId(), "CATALOG_BRAND_CREATE", "BRAND", brand.brandId());
		return ResponseEntity.created(URI.create("/api/admin/brands/" + brand.brandId())).body(brand);
	}

	@GetMapping("/brands/{brandId}")
	AdminCatalogViews.Brand brand(@PathVariable long brandId) { return catalogExpansionAdminService.brand(brandId); }

	@PatchMapping("/brands/{brandId}") @Transactional
	AdminCatalogViews.Brand updateBrand(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long brandId,
			@RequestBody AdminCatalogRequests.BrandPatch request) {
		AdminCatalogViews.Brand brand = catalogExpansionAdminService.updateBrand(brandId, request);
		audits.append(principal.memberId(), "CATALOG_BRAND_UPDATE", "BRAND", brandId);
		return brand;
	}

	@GetMapping("/categories")
	AdminCatalogViews.CategoryList categories() { return adminCatalogService.categories(); }

	@PostMapping("/categories")
	@Transactional
	ResponseEntity<AdminCatalogViews.Category> createCategory(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@Valid @RequestBody AdminCatalogRequests.CategoryCreate request) {
		AdminCatalogViews.Category category = adminCatalogService.createCategory(request);
		audits.append(principal.memberId(), "CATALOG_CATEGORY_CREATE", "CATEGORY", category.categoryId());
		return ResponseEntity.created(URI.create("/api/admin/categories/" + category.categoryId())).body(category);
	}

	@GetMapping("/categories/{categoryId}")
	AdminCatalogViews.Category category(@PathVariable Long categoryId) { return adminCatalogService.category(categoryId); }

	@PatchMapping("/categories/{categoryId}")
	@Transactional
	AdminCatalogViews.Category updateCategory(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable Long categoryId,
			@RequestBody AdminCatalogRequests.CategoryPatch request) {
		AdminCatalogViews.Category category = adminCatalogService.updateCategory(categoryId, request);
		audits.append(principal.memberId(), "CATALOG_CATEGORY_UPDATE", "CATEGORY", categoryId);
		return category;
	}

	@GetMapping("/products")
	AdminCatalogViews.ProductList products() { return adminCatalogService.products(); }

	@PostMapping("/products")
	@Transactional
	ResponseEntity<AdminCatalogViews.Product> createProduct(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@Valid @RequestBody AdminCatalogRequests.ProductCreate request) {
		AdminCatalogViews.Product product = adminCatalogService.createProduct(request);
		audits.append(principal.memberId(), "CATALOG_PRODUCT_CREATE", "PRODUCT", product.productId());
		return ResponseEntity.created(URI.create("/api/admin/products/" + product.productId())).body(product);
	}

	@GetMapping("/products/{productId}")
	AdminCatalogViews.Product product(@PathVariable Long productId) { return adminCatalogService.product(productId); }

	@PatchMapping("/products/{productId}")
	@Transactional
	AdminCatalogViews.Product updateProduct(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable Long productId,
			@RequestBody AdminCatalogRequests.ProductPatch request) {
		AdminCatalogViews.Product product = adminCatalogService.updateProduct(productId, request);
		audits.append(principal.memberId(), "CATALOG_PRODUCT_UPDATE", "PRODUCT", productId);
		return product;
	}

	@GetMapping("/products/{productId}/skus")
	AdminCatalogViews.SkuList skus(@PathVariable Long productId) { return adminCatalogService.skus(productId); }

	@PostMapping("/products/{productId}/skus")
	@Transactional
	ResponseEntity<AdminCatalogViews.Sku> createSku(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable Long productId,
			@Valid @RequestBody AdminCatalogRequests.SkuCreate request) {
		AdminCatalogViews.Sku sku = adminCatalogService.createSku(productId, request);
		audits.append(principal.memberId(), "CATALOG_SKU_CREATE", "SKU", sku.skuId());
		return ResponseEntity.created(URI.create("/api/admin/products/" + productId + "/skus/" + sku.skuId())).body(sku);
	}

	@PatchMapping("/products/{productId}/skus/{skuId}")
	@Transactional
	AdminCatalogViews.Sku updateSku(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable Long productId,
			@PathVariable Long skuId,
			@RequestBody AdminCatalogRequests.SkuPatch request) {
		AdminCatalogViews.Sku sku = adminCatalogService.updateSku(productId, skuId, request);
		audits.append(principal.memberId(), "CATALOG_SKU_UPDATE", "SKU", skuId);
		return sku;
	}

	@GetMapping("/products/{productId}/images")
	AdminCatalogViews.ImageList images(@PathVariable long productId) { return catalogExpansionAdminService.images(productId); }
	@PostMapping("/products/{productId}/images") @Transactional
	ResponseEntity<AdminCatalogViews.Image> createImage(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long productId,
			@Valid @RequestBody AdminCatalogRequests.ImageCreate request) {
		AdminCatalogViews.Image image = catalogExpansionAdminService.createImage(productId, request);
		audits.append(principal.memberId(), "CATALOG_PRODUCT_IMAGE_CREATE", "PRODUCT_IMAGE", image.imageId());
		return ResponseEntity.created(URI.create("/api/admin/products/" + productId + "/images/" + image.imageId())).body(image);
	}
	@PatchMapping("/products/{productId}/images/{imageId}") @Transactional
	AdminCatalogViews.Image updateImage(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long productId, @PathVariable long imageId,
			@RequestBody AdminCatalogRequests.ImagePatch request) {
		AdminCatalogViews.Image image = catalogExpansionAdminService.updateImage(productId, imageId, request);
		audits.append(principal.memberId(), "CATALOG_PRODUCT_IMAGE_UPDATE", "PRODUCT_IMAGE", imageId); return image;
	}
	@DeleteMapping("/products/{productId}/images/{imageId}") @Transactional
	void deleteImage(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long productId, @PathVariable long imageId) {
		catalogExpansionAdminService.deleteImage(productId, imageId); audits.append(principal.memberId(), "CATALOG_PRODUCT_IMAGE_DELETE", "PRODUCT_IMAGE", imageId);
	}

	@GetMapping("/products/{productId}/option-groups")
	AdminCatalogViews.OptionGroupList optionGroups(@PathVariable long productId) { return catalogExpansionAdminService.optionGroups(productId); }
	@PostMapping("/products/{productId}/option-groups") @Transactional
	ResponseEntity<AdminCatalogViews.OptionGroup> createOptionGroup(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long productId,
			@Valid @RequestBody AdminCatalogRequests.OptionGroupCreate request) {
		AdminCatalogViews.OptionGroup group = catalogExpansionAdminService.createOptionGroup(productId, request); audits.append(principal.memberId(), "CATALOG_OPTION_GROUP_CREATE", "PRODUCT_OPTION_GROUP", group.optionGroupId());
		return ResponseEntity.created(URI.create("/api/admin/products/" + productId + "/option-groups/" + group.optionGroupId())).body(group);
	}
	@PatchMapping("/products/{productId}/option-groups/{groupId}") @Transactional
	AdminCatalogViews.OptionGroup updateOptionGroup(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long productId, @PathVariable long groupId, @RequestBody AdminCatalogRequests.OptionGroupPatch request) {
		AdminCatalogViews.OptionGroup group = catalogExpansionAdminService.updateOptionGroup(productId, groupId, request); audits.append(principal.memberId(), "CATALOG_OPTION_GROUP_UPDATE", "PRODUCT_OPTION_GROUP", groupId); return group;
	}
	@DeleteMapping("/products/{productId}/option-groups/{groupId}") @Transactional
	void deleteOptionGroup(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long productId, @PathVariable long groupId) { catalogExpansionAdminService.deleteOptionGroup(productId,groupId); audits.append(principal.memberId(),"CATALOG_OPTION_GROUP_DELETE","PRODUCT_OPTION_GROUP",groupId); }
	@PostMapping("/products/{productId}/option-groups/{groupId}/values") @Transactional
	ResponseEntity<AdminCatalogViews.OptionValue> createOptionValue(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long productId,@PathVariable long groupId,@Valid @RequestBody AdminCatalogRequests.OptionValueCreate request) {
		AdminCatalogViews.OptionValue value=catalogExpansionAdminService.createOptionValue(productId,groupId,request);audits.append(principal.memberId(),"CATALOG_OPTION_VALUE_CREATE","PRODUCT_OPTION_VALUE",value.optionValueId());return ResponseEntity.created(URI.create("/api/admin/products/"+productId+"/option-groups/"+groupId+"/values/"+value.optionValueId())).body(value);
	}
	@PatchMapping("/products/{productId}/option-groups/{groupId}/values/{valueId}") @Transactional
	AdminCatalogViews.OptionValue updateOptionValue(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long productId,@PathVariable long groupId,@PathVariable long valueId,@RequestBody AdminCatalogRequests.OptionValuePatch request) { AdminCatalogViews.OptionValue value=catalogExpansionAdminService.updateOptionValue(productId,groupId,valueId,request);audits.append(principal.memberId(),"CATALOG_OPTION_VALUE_UPDATE","PRODUCT_OPTION_VALUE",valueId);return value; }
	@DeleteMapping("/products/{productId}/option-groups/{groupId}/values/{valueId}") @Transactional
	void deleteOptionValue(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long productId,@PathVariable long groupId,@PathVariable long valueId) {catalogExpansionAdminService.deleteOptionValue(productId,groupId,valueId);audits.append(principal.memberId(),"CATALOG_OPTION_VALUE_DELETE","PRODUCT_OPTION_VALUE",valueId);}
	@PutMapping("/products/{productId}/skus/{skuId}/option-values") @Transactional
	AdminCatalogViews.SkuOptionValues setSkuOptionValues(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long productId,@PathVariable long skuId,@Valid @RequestBody AdminCatalogRequests.SkuOptionValues request) {AdminCatalogViews.SkuOptionValues values=catalogExpansionAdminService.setSkuOptionValues(productId,skuId,request);audits.append(principal.memberId(),"CATALOG_SKU_OPTION_VALUES_SET","SKU",skuId);return values;}
	@GetMapping("/products/{productId}/skus/{skuId}/option-values")
	AdminCatalogViews.SkuOptionValues skuOptionValues(@PathVariable long productId,@PathVariable long skuId){return catalogExpansionAdminService.skuOptionValues(productId,skuId);}

	@GetMapping("/facets")
	AdminCatalogViews.FacetDefinitionList facetDefinitions() { return catalogExpansionAdminService.facetDefinitions(); }
	@GetMapping("/facets/{definitionId}")
	AdminCatalogViews.FacetDefinition facetDefinition(@PathVariable long definitionId) { return catalogExpansionAdminService.facetDefinition(definitionId); }
	@PostMapping("/facets") @Transactional
	ResponseEntity<AdminCatalogViews.FacetDefinition> createFacetDefinition(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@Valid @RequestBody AdminCatalogRequests.FacetDefinitionCreate request) {AdminCatalogViews.FacetDefinition definition=catalogExpansionAdminService.createFacetDefinition(request);audits.append(principal.memberId(),"CATALOG_FACET_CREATE","FACET_DEFINITION",definition.facetDefinitionId());return ResponseEntity.created(URI.create("/api/admin/facets/"+definition.facetDefinitionId())).body(definition);}
	@PatchMapping("/facets/{definitionId}") @Transactional
	AdminCatalogViews.FacetDefinition updateFacetDefinition(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long definitionId,@RequestBody AdminCatalogRequests.FacetDefinitionPatch request) {AdminCatalogViews.FacetDefinition definition=catalogExpansionAdminService.updateFacetDefinition(definitionId,request);audits.append(principal.memberId(),"CATALOG_FACET_UPDATE","FACET_DEFINITION",definitionId);return definition;}
	@DeleteMapping("/facets/{definitionId}") @Transactional
	void deleteFacetDefinition(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long definitionId) {catalogExpansionAdminService.deleteFacetDefinition(definitionId);audits.append(principal.memberId(),"CATALOG_FACET_DELETE","FACET_DEFINITION",definitionId);}
	@PostMapping("/facets/{definitionId}/options") @Transactional
	ResponseEntity<AdminCatalogViews.FacetOption> createFacetOption(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long definitionId,@Valid @RequestBody AdminCatalogRequests.FacetOptionCreate request) {AdminCatalogViews.FacetOption option=catalogExpansionAdminService.createFacetOption(definitionId,request);audits.append(principal.memberId(),"CATALOG_FACET_OPTION_CREATE","FACET_OPTION",option.facetOptionId());return ResponseEntity.created(URI.create("/api/admin/facets/"+definitionId+"/options/"+option.facetOptionId())).body(option);}
	@PatchMapping("/facets/{definitionId}/options/{optionId}") @Transactional
	AdminCatalogViews.FacetOption updateFacetOption(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long definitionId,@PathVariable long optionId,@RequestBody AdminCatalogRequests.FacetOptionPatch request) {AdminCatalogViews.FacetOption option=catalogExpansionAdminService.updateFacetOption(definitionId,optionId,request);audits.append(principal.memberId(),"CATALOG_FACET_OPTION_UPDATE","FACET_OPTION",optionId);return option;}
	@DeleteMapping("/facets/{definitionId}/options/{optionId}") @Transactional
	void deleteFacetOption(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long definitionId,@PathVariable long optionId) {catalogExpansionAdminService.deleteFacetOption(definitionId,optionId);audits.append(principal.memberId(),"CATALOG_FACET_OPTION_DELETE","FACET_OPTION",optionId);}
	@PutMapping("/categories/{categoryId}/facets/{definitionId}") @Transactional
	AdminCatalogViews.CategoryFacet assignCategoryFacet(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long categoryId,@PathVariable long definitionId,@Valid @RequestBody AdminCatalogRequests.CategoryFacetAssign request) {AdminCatalogViews.CategoryFacet facet=catalogExpansionAdminService.assignCategoryFacet(categoryId,definitionId,request);audits.append(principal.memberId(),"CATALOG_CATEGORY_FACET_SET","CATEGORY",categoryId);return facet;}
	@DeleteMapping("/categories/{categoryId}/facets/{definitionId}") @Transactional
	void removeCategoryFacet(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long categoryId,@PathVariable long definitionId) {catalogExpansionAdminService.removeCategoryFacet(categoryId,definitionId);audits.append(principal.memberId(),"CATALOG_CATEGORY_FACET_DELETE","CATEGORY",categoryId);}
	@PutMapping("/products/{productId}/facet-values") @Transactional
	AdminCatalogViews.ProductFacetValues setProductFacetValues(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long productId,@Valid @RequestBody AdminCatalogRequests.ProductFacetValues request) {AdminCatalogViews.ProductFacetValues values=catalogExpansionAdminService.setProductFacetValues(productId,request);audits.append(principal.memberId(),"CATALOG_PRODUCT_FACET_VALUES_SET","PRODUCT",productId);return values;}
	@GetMapping("/products/{productId}/facet-values")
	AdminCatalogViews.ProductFacetValues productFacetValues(@PathVariable long productId){return catalogExpansionAdminService.productFacetValues(productId);}
	@GetMapping("/categories/{categoryId}/facets")
	AdminCatalogViews.CategoryFacetList categoryFacets(@PathVariable long categoryId){return catalogExpansionAdminService.categoryFacets(categoryId);}

	@GetMapping("/products/{productId}/detail-sections")
	AdminCatalogViews.DetailSectionList detailSections(@PathVariable Long productId) {
		return productDetailSectionService.list(productId);
	}

	@PostMapping("/products/{productId}/detail-sections")
	@Transactional
	ResponseEntity<AdminCatalogViews.DetailSection> createDetailSection(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable Long productId,
			@Valid @RequestBody AdminCatalogRequests.DetailSectionCreate request) {
		AdminCatalogViews.DetailSection section = productDetailSectionService.create(productId, request);
		audits.append(principal.memberId(), "CATALOG_DETAIL_SECTION_CREATE", "PRODUCT_DETAIL_SECTION", section.sectionId());
		return ResponseEntity.created(URI.create("/api/admin/products/" + productId + "/detail-sections/" + section.sectionId())).body(section);
	}

	@PatchMapping("/products/{productId}/detail-sections/{sectionId}")
	@Transactional
	AdminCatalogViews.DetailSection updateDetailSection(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable Long productId, @PathVariable Long sectionId,
			@RequestBody AdminCatalogRequests.DetailSectionPatch request) {
		AdminCatalogViews.DetailSection section = productDetailSectionService.update(productId, sectionId, request);
		audits.append(principal.memberId(), "CATALOG_DETAIL_SECTION_UPDATE", "PRODUCT_DETAIL_SECTION", sectionId);
		return section;
	}

	@DeleteMapping("/products/{productId}/detail-sections/{sectionId}")
	@Transactional
	void deleteDetailSection(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable Long productId, @PathVariable Long sectionId) {
		productDetailSectionService.delete(productId, sectionId);
		audits.append(principal.memberId(), "CATALOG_DETAIL_SECTION_DELETE", "PRODUCT_DETAIL_SECTION", sectionId);
	}
}
