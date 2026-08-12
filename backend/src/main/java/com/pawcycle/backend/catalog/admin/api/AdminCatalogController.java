package com.pawcycle.backend.catalog.admin.api;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

	@GetMapping("/categories")
	AdminCatalogViews.CategoryList categories() {
		return adminCatalogService.categories();
	}

	@PostMapping("/categories")
	ResponseEntity<AdminCatalogViews.Category> createCategory(
			@Valid @RequestBody AdminCatalogRequests.CategoryCreate request) {
		AdminCatalogViews.Category category = adminCatalogService.createCategory(request);
		return ResponseEntity.created(URI.create("/api/admin/categories/" + category.categoryId())).body(category);
	}

	@GetMapping("/categories/{categoryId}")
	AdminCatalogViews.Category category(@PathVariable Long categoryId) {
		return adminCatalogService.category(categoryId);
	}

	@PatchMapping("/categories/{categoryId}")
	AdminCatalogViews.Category updateCategory(
			@PathVariable Long categoryId,
			@RequestBody AdminCatalogRequests.CategoryPatch request) {
		return adminCatalogService.updateCategory(categoryId, request);
	}

	@GetMapping("/products")
	AdminCatalogViews.ProductList products() {
		return adminCatalogService.products();
	}

	@PostMapping("/products")
	ResponseEntity<AdminCatalogViews.Product> createProduct(
			@Valid @RequestBody AdminCatalogRequests.ProductCreate request) {
		AdminCatalogViews.Product product = adminCatalogService.createProduct(request);
		return ResponseEntity.created(URI.create("/api/admin/products/" + product.productId())).body(product);
	}

	@GetMapping("/products/{productId}")
	AdminCatalogViews.Product product(@PathVariable Long productId) {
		return adminCatalogService.product(productId);
	}

	@PatchMapping("/products/{productId}")
	AdminCatalogViews.Product updateProduct(
			@PathVariable Long productId,
			@RequestBody AdminCatalogRequests.ProductPatch request) {
		return adminCatalogService.updateProduct(productId, request);
	}

	@GetMapping("/products/{productId}/skus")
	AdminCatalogViews.SkuList skus(@PathVariable Long productId) {
		return adminCatalogService.skus(productId);
	}

	@PostMapping("/products/{productId}/skus")
	ResponseEntity<AdminCatalogViews.Sku> createSku(
			@PathVariable Long productId,
			@Valid @RequestBody AdminCatalogRequests.SkuCreate request) {
		AdminCatalogViews.Sku sku = adminCatalogService.createSku(productId, request);
		return ResponseEntity.created(URI.create(
				"/api/admin/products/" + productId + "/skus/" + sku.skuId())).body(sku);
	}

	@PatchMapping("/products/{productId}/skus/{skuId}")
	AdminCatalogViews.Sku updateSku(
			@PathVariable Long productId,
			@PathVariable Long skuId,
			@RequestBody AdminCatalogRequests.SkuPatch request) {
		return adminCatalogService.updateSku(productId, skuId, request);
	}
}
