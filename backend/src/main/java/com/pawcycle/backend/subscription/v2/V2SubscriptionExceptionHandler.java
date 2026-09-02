package com.pawcycle.backend.subscription.v2;

import com.pawcycle.backend.common.error.ApiErrorResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(
    assignableTypes = {V2SubscriptionController.class, RepeatCommerceController.class})
public class V2SubscriptionExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(V2SubscriptionExceptionHandler.class);

  @ExceptionHandler(V2ApiException.class)
  ResponseEntity<ApiErrorResponse> v2(V2ApiException error) {
    return ResponseEntity.status(error.status())
        .body(new ApiErrorResponse(error.code(), error.getMessage(), List.of()));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiErrorResponse> binding(Exception error) {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("VALIDATION_FAILED", "입력 값을 확인해 주세요.", List.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
    log.error("Unexpected exception while processing subscription V2 request", exception);
    return ResponseEntity.internalServerError()
        .body(ApiErrorResponse.withoutFieldErrors("INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
  }
}
