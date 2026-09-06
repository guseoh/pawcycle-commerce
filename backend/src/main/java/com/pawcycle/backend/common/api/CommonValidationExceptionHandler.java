package com.pawcycle.backend.common.api;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 여러 MVC endpoint의 framework-level validation 오류를 하나의 HTTP 계약으로 변환한다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {
  "com.pawcycle.backend.commerce",
  "com.pawcycle.backend.catalog.admin.api",
  "com.pawcycle.backend.catalog.engagement.api",
  "com.pawcycle.backend.member.address.api",
  "com.pawcycle.backend.subscription"
})
public class CommonValidationExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> methodArgumentNotValid(MethodArgumentNotValidException exception) {
    List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
        .sorted(Comparator.comparing(org.springframework.validation.FieldError::getField)
            .thenComparing(error -> Objects.requireNonNullElse(error.getDefaultMessage(), "")))
        .map(error -> new FieldErrorResponse(error.getField(), Objects.requireNonNullElse(error.getDefaultMessage(), "요청 값이 올바르지 않습니다.")))
        .toList();
    return validation(fieldErrors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiErrorResponse> malformedJson(HttpMessageNotReadableException exception) {
    return validation(List.of());
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ApiErrorResponse> missingParameter(MissingServletRequestParameterException exception) {
    return validation(List.of(new FieldErrorResponse(exception.getParameterName(), "요청 파라미터가 필요합니다.")));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception) {
    return validation(List.of(new FieldErrorResponse(exception.getName(), "요청 값의 형식이 올바르지 않습니다.")));
  }

  private ResponseEntity<ApiErrorResponse> validation(List<FieldErrorResponse> fieldErrors) {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("VALIDATION_FAILED", "요청 값을 확인해 주세요.", fieldErrors));
  }
}
