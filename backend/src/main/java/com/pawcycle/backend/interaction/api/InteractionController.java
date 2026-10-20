package com.pawcycle.backend.interaction.api;

import com.pawcycle.backend.interaction.application.InteractionEventCommand;
import com.pawcycle.backend.interaction.application.InteractionService;
import java.util.List;

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
    List<InteractionEventCommand> events = request == null || request.events() == null
        ? null
        : request.events().stream()
            .map(event -> event == null ? null : new InteractionEventCommand(
                event.eventId(), event.type(), event.productId(), event.petId(),
                event.source(), event.recommendationRequestId(), event.context()))
            .toList();
    service.record(principal.memberId(), events);
    return ResponseEntity.noContent().build();
  }
}
