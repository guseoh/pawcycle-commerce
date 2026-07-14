package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import com.pawcycle.backend.subscription.application.SkuNotFoundException;
import com.pawcycle.backend.subscription.application.SkuNotSubscribableException;
import com.pawcycle.backend.subscription.application.SubscriptionCreateFailedException;
import com.pawcycle.backend.subscription.application.SubscriptionDetailUnavailableException;
import com.pawcycle.backend.subscription.application.SubscriptionListUnavailableException;
import com.pawcycle.backend.subscription.application.SubscriptionNotFoundException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

@RestControllerAdvice(assignableTypes = SubscriptionController.class)
public class SubscriptionExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(SubscriptionExceptionHandler.class);
	private static final Set<String> REQUEST_FIELDS = Set.of("skuId", "quantity", "deliveryCycleWeeks");

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
				.toList();
		return ResponseEntity.badRequest().body(new ApiErrorResponse(
				"VALIDATION_FAILED", "입력 내용을 확인해 주세요.", fieldErrors));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> handleMalformedJson(HttpMessageNotReadableException exception) {
		return ResponseEntity.badRequest().body(new ApiErrorResponse(
				"VALIDATION_FAILED",
				"입력 내용을 확인해 주세요.",
				List.of(new FieldErrorResponse(resolveJsonErrorField(exception), "요청 값의 형식을 확인해 주세요."))));
	}

	private String resolveJsonErrorField(HttpMessageNotReadableException exception) {
		Throwable cause = exception.getCause();
		while (cause != null) {
			if (cause instanceof JacksonException jacksonException) {
				return jacksonException.getPath().stream()
						.map(JacksonException.Reference::getPropertyName)
						.filter(REQUEST_FIELDS::contains)
						.reduce((first, last) -> last)
						.orElse("request");
			}
			cause = cause.getCause();
		}
		return "request";
	}

	@ExceptionHandler(SkuNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleSkuNotFound(SkuNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.withoutFieldErrors(
				"SKU_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(SkuNotSubscribableException.class)
	ResponseEntity<ApiErrorResponse> handleSkuNotSubscribable(SkuNotSubscribableException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.withoutFieldErrors(
				"SKU_NOT_SUBSCRIBABLE", exception.getMessage()));
	}

	@ExceptionHandler(SubscriptionNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleSubscriptionNotFound(SubscriptionNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.withoutFieldErrors(
				"SUBSCRIPTION_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(SubscriptionCreateFailedException.class)
	ResponseEntity<ApiErrorResponse> handleCreateFailed(SubscriptionCreateFailedException exception) {
		log.error("Unexpected exception while creating subscription");
		return ResponseEntity.internalServerError().body(ApiErrorResponse.withoutFieldErrors(
				"SUBSCRIPTION_CREATE_FAILED", "구독을 생성하지 못했습니다."));
	}

	@ExceptionHandler(SubscriptionListUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleListUnavailable(SubscriptionListUnavailableException exception) {
		log.error("Unexpected exception while querying subscription list");
		return ResponseEntity.internalServerError().body(ApiErrorResponse.withoutFieldErrors(
				"SUBSCRIPTION_LIST_UNAVAILABLE", "구독 목록을 불러오지 못했습니다."));
	}

	@ExceptionHandler(SubscriptionDetailUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleDetailUnavailable(SubscriptionDetailUnavailableException exception) {
		log.error("Unexpected exception while querying subscription detail");
		return ResponseEntity.internalServerError().body(ApiErrorResponse.withoutFieldErrors(
				"SUBSCRIPTION_DETAIL_UNAVAILABLE", "구독 정보를 불러오지 못했습니다."));
	}
}
