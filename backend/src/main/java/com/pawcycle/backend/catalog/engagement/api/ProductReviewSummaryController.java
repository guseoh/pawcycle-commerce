package com.pawcycle.backend.catalog.engagement.api;

import com.pawcycle.backend.catalog.product.application.ProductNotFoundException;
import com.pawcycle.backend.catalog.engagement.application.ReviewSummaryService;
import com.pawcycle.backend.common.error.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductReviewSummaryController {
	private final ReviewSummaryService service;
	public ProductReviewSummaryController(ReviewSummaryService service){this.service=service;}
	@GetMapping("/api/products/{productId}/reviews/summary") ReviewSummaryService.ReviewSummaryResponse summary(@PathVariable long productId){return service.summary(productId);}
}

@RestControllerAdvice(assignableTypes = ProductReviewSummaryController.class)
class ProductReviewSummaryExceptionHandler {
	@ExceptionHandler(ProductNotFoundException.class)
	ResponseEntity<ApiErrorResponse> notFound() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiErrorResponse.withoutFieldErrors("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
	}
}
