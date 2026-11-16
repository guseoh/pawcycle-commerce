package com.pawcycle.backend.common.error;

import com.pawcycle.backend.common.logging.RedactedFailure;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;

/** Shared catch-all behaviour for controller advices that end with {@code @ExceptionHandler(Exception.class)}. */
public final class UnexpectedErrors {
  private UnexpectedErrors() {}

  /**
   * Turns an unexpected exception into a 500 {@link ApiErrorResponse} and logs it once with the
   * stack trace but without exception messages. Spring's own request errors (405, 415, 404 for a
   * missing resource and similar {@link ErrorResponse} types) are rethrown so Spring keeps their
   * status instead of reporting them as 500.
   */
  public static ResponseEntity<ApiErrorResponse> internalError(
      Logger log, String area, Exception exception) throws Exception {
    if (exception instanceof ErrorResponse) throw exception;
    log.error(
        "Unexpected exception; area={}, cause={}",
        area, exception.getClass().getSimpleName(), RedactedFailure.of(exception));
    return ResponseEntity.internalServerError()
        .body(ApiErrorResponse.withoutFieldErrors("INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
  }
}
