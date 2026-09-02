package com.pawcycle.backend.interaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InteractionController.class)
class InteractionExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(InteractionExceptionHandler.class);

  @ExceptionHandler(InteractionException.class)
  ResponseEntity<InteractionErrorResponse> handle(InteractionException exception) {
    return ResponseEntity.status(exception.status())
        .body(new InteractionErrorResponse(exception.code(), exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<InteractionErrorResponse> unexpected(Exception exception) {
    log.error("Unexpected exception while recording interaction", exception);
    return ResponseEntity.internalServerError()
        .body(new InteractionErrorResponse("INTERNAL_ERROR", "요청을 처리할 수 없습니다."));
  }
}
