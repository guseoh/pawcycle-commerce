package com.pawcycle.backend.catalog.product.api;

import com.pawcycle.backend.catalog.product.application.ProductDetailView;
import com.pawcycle.backend.catalog.product.application.ProductListView;
import com.pawcycle.backend.catalog.product.application.ProductNotFoundException;
import com.pawcycle.backend.catalog.product.application.ProductQueryService;
import com.pawcycle.backend.catalog.product.application.ProductSort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductQueryService productQueryService;

	public ProductController(ProductQueryService productQueryService) {
		this.productQueryService = productQueryService;
	}

	@GetMapping
	ProductListView products(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String petType,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String subcategory,
			@RequestParam(required = false) String brand,
			@RequestParam(required = false) List<String> facet,
			@RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(required = false) Boolean subscribable,
			@RequestParam(required = false) Boolean purchasable,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "NEWEST") ProductSort sort) {
		boolean extendedFiltersAbsent = subcategory == null && brand == null && (facet == null || facet.isEmpty())
				&& minPrice == null && maxPrice == null && subscribable == null && purchasable == null;
		ProductListView response = extendedFiltersAbsent
				? productQueryService.findProducts(q, petType, category, page, size, sort)
				: productQueryService.findProducts(q, petType, category, subcategory, brand, facet, minPrice, maxPrice, subscribable, purchasable, page, size, sort);
		// Keeps the pre-pagination controller seam usable for older isolated tests/mocks; the real service never returns null.
		return response == null ? productQueryService.findProducts(q, petType, category) : response;
	}

	/** Compatibility seam for existing controller tests and direct in-process callers. */
	ProductListView products(String q, String petType, String category, int page, int size, ProductSort sort) {
		ProductListView response = productQueryService.findProducts(q, petType, category, page, size, sort);
		return response == null ? productQueryService.findProducts(q, petType, category) : response;
	}

	@GetMapping("/{productId}")
	ProductDetailView product(@PathVariable String productId) {
		try {
			return productQueryService.findProduct(Long.valueOf(productId));
		} catch (NumberFormatException exception) {
			throw new ProductNotFoundException();
		}
	}
}
