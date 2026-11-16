package com.pawcycle.backend.interaction.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pawcycle.backend.common.error.ApiErrorResponse;

/** Interaction error body; {@code requestId} follows the common error contract. */
public record InteractionErrorResponse(
    String code, String message, @JsonInclude(JsonInclude.Include.NON_NULL) String requestId) {

  public InteractionErrorResponse(String code, String message) {
    this(code, message, ApiErrorResponse.currentRequestId());
  }
}
