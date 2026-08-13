package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCatalogController {
	private final AdminCatalogService adminCatalogService;
	private final AdminAuditService audits;

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
}
