package com.pawcycle.backend.interaction;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InteractionController {
  private final InteractionService service;

  public InteractionController(InteractionService service) {
    this.service = service;
  }

  @PostMapping("/api/interactions")
  ResponseEntity<Void> record(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @RequestBody Map<String, Object> body) {
    Object value = body == null ? null : body.get("events");
    if (!(value instanceof List<?> list))
      throw new InteractionException(400, "VALIDATION_FAILED", "events 값을 확인해 주세요.");
    List<Map<String, Object>> events = new java.util.ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> raw))
        throw new InteractionException(400, "VALIDATION_FAILED", "event 값을 확인해 주세요.");
      events.add((Map<String, Object>) raw);
    }
    service.record(principal.memberId(), events);
    return ResponseEntity.noContent().build();
  }
}
