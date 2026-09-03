package com.pawcycle.backend.catalog.product.api;

import com.pawcycle.backend.catalog.product.application.ProductComparisonService;
import com.pawcycle.backend.catalog.product.application.ProductComparisonResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductComparisonController {
  private final ProductComparisonService service;

  public ProductComparisonController(ProductComparisonService service) {
    this.service = service;
  }

  @GetMapping("/api/products/compare")
  ProductComparisonResponse compare(
      @RequestParam("productId") List<Long> productIds) {
    return service.compare(productIds);
  }
}
