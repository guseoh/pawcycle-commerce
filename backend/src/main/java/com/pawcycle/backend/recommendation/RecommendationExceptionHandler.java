package com.pawcycle.backend.recommendation;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {RecommendationController.class, ProductRecommendationController.class})
class RecommendationExceptionHandler {
	@ExceptionHandler(RecommendationException.class)
	ResponseEntity<ApiErrorResponse> handleRecommendation(RecommendationException exception) { return ResponseEntity.status(exception.status()).body(ApiErrorResponse.withoutFieldErrors(exception.code(), exception.getMessage())); }
	@ExceptionHandler(RecommendationPetNotFoundException.class)
	ResponseEntity<ApiErrorResponse> petNotFound() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiErrorResponse.withoutFieldErrors("PET_NOT_FOUND", "Pet을 찾을 수 없습니다."));
	}
}
