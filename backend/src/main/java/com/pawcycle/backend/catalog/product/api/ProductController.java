package com.pawcycle.backend.catalog.product.api;

import com.pawcycle.backend.catalog.product.application.ProductDetailView;
import com.pawcycle.backend.catalog.product.application.ProductListView;
import com.pawcycle.backend.catalog.product.application.ProductNotFoundException;
import com.pawcycle.backend.catalog.product.application.ProductQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductQueryService productQueryService;

	public ProductController(ProductQueryService productQueryService) {
		this.productQueryService = productQueryService;
	}

	@GetMapping
	ProductListView products() {
		return productQueryService.findProducts();
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
