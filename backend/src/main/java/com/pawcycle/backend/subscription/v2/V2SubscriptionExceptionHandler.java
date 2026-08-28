package com.pawcycle.backend.subscription.v2;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {V2SubscriptionController.class, RepeatCommerceController.class})
public class V2SubscriptionExceptionHandler {
	@ExceptionHandler(V2ApiException.class)
	ResponseEntity<ApiErrorResponse> v2(V2ApiException error) {
		return ResponseEntity.status(error.status()).body(new ApiErrorResponse(error.code(), error.getMessage(), List.of()));
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
	ResponseEntity<ApiErrorResponse> binding(Exception error) {
		return ResponseEntity.badRequest().body(new ApiErrorResponse("VALIDATION_FAILED", "입력 값을 확인해 주세요.", List.of()));
	}
}
