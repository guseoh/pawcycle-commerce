package com.pawcycle.backend.interaction;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class InteractionExceptionHandler {
	@ExceptionHandler(InteractionException.class)
	ResponseEntity<Map<String, Object>> handle(InteractionException exception) { return ResponseEntity.status(exception.status()).body(Map.of("code", exception.code(), "message", exception.getMessage())); }
}
