package com.pawcycle.backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pawcycle.backend.common.web.RequestCorrelationFilter;
import java.util.List;
import org.slf4j.MDC;

/**
 * Common error body. {@code requestId} is the same value as the {@code X-Request-ID} response header
 * and the {@code requestId} field of backend logs; it is omitted only outside an HTTP request.
 */
public record ApiErrorResponse(
    String code,
    String message,
    List<FieldErrorResponse> fieldErrors,
    @JsonInclude(JsonInclude.Include.NON_NULL) String requestId) {

  public ApiErrorResponse(String code, String message, List<FieldErrorResponse> fieldErrors) {
    this(code, message, fieldErrors, currentRequestId());
  }

  public static ApiErrorResponse withoutFieldErrors(String code, String message) {
    return new ApiErrorResponse(code, message, List.of());
  }

  /** The request id of the current HTTP request, or null outside one. */
  public static String currentRequestId() {
    return MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
  }
}
