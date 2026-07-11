package com.pawcycle.backend.member.api;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import com.pawcycle.backend.member.application.AuthValidationException;
import com.pawcycle.backend.member.application.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

	@ExceptionHandler(AuthValidationException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(AuthValidationException exception) {
		return ResponseEntity.badRequest().body(new ApiErrorResponse(
				"VALIDATION_FAILED", exception.getMessage(), exception.getFieldErrors()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> handleMalformedJson() {
		return ResponseEntity.badRequest().body(ApiErrorResponse.withoutFieldErrors(
				"VALIDATION_FAILED", "요청 본문이 유효하지 않습니다."));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.withoutFieldErrors(
				"INVALID_CREDENTIALS", exception.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpectedException() {
		return ResponseEntity.internalServerError().body(ApiErrorResponse.withoutFieldErrors(
				"INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
	}
}
