package com.pawcycle.backend.commerce.cancellation.api;

import com.pawcycle.backend.commerce.CancellationService;
import com.pawcycle.backend.commerce.ReasonRequest;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/cancellations")
public class CancellationController {
  private final CancellationService cancellations;

  public CancellationController(CancellationService cancellations) {
    this.cancellations = cancellations;
  }

  @PostMapping
  public CancellationResponse request(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long orderId,
      @Valid @RequestBody ReasonRequest request) {
    return cancellations.request(principal.memberId(), orderId, request.reason());
  }
}
