package com.pawcycle.backend.subscription.v2;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = V2SubscriptionController.class)
public class V2SubscriptionExceptionHandler {
	@ExceptionHandler(V2ApiException.class)
	ResponseEntity<ApiErrorResponse> v2(V2ApiException error) {
		return ResponseEntity.status(error.status()).body(new ApiErrorResponse(error.code(), error.getMessage(), List.of()));
	}
}
