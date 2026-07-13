package com.pawcycle.backend.catalog.product.api;

import com.pawcycle.backend.catalog.product.application.ProductDetailUnavailableException;
import com.pawcycle.backend.catalog.product.application.ProductListUnavailableException;
import com.pawcycle.backend.catalog.product.application.ProductNotFoundException;
import com.pawcycle.backend.common.error.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(ProductExceptionHandler.class);

	@ExceptionHandler(ProductNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNotFound(ProductNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.withoutFieldErrors(
				"PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
	}

	@ExceptionHandler(ProductListUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleListUnavailable(ProductListUnavailableException exception) {
		log.error("Unexpected exception while querying public product list", exception);
		return ResponseEntity.internalServerError().body(ApiErrorResponse.withoutFieldErrors(
				"PRODUCT_LIST_UNAVAILABLE", "상품 목록을 불러오지 못했습니다."));
	}

	@ExceptionHandler(ProductDetailUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleDetailUnavailable(ProductDetailUnavailableException exception) {
		log.error("Unexpected exception while querying public product detail", exception);
		return ResponseEntity.internalServerError().body(ApiErrorResponse.withoutFieldErrors(
				"PRODUCT_DETAIL_UNAVAILABLE", "상품 정보를 불러오지 못했습니다."));
	}
}
