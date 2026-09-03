package com.pawcycle.backend.interaction;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
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
      @RequestBody InteractionBatchRequest request) {
    service.record(principal.memberId(), request == null ? null : request.events());
    return ResponseEntity.noContent().build();
  }
}
