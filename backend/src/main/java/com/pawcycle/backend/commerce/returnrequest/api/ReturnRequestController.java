package com.pawcycle.backend.commerce.returnrequest.api;

import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.commerce.ReasonRequest;
import com.pawcycle.backend.commerce.ReturnService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/returns")
public class ReturnRequestController {
  private final ReturnService returns;

  public ReturnRequestController(ReturnService returns) {
    this.returns = returns;
  }

  @PostMapping
  public CommercePayload request(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long orderId,
      @Valid @RequestBody ReasonRequest request) {
    return returns.request(principal.memberId(), orderId, request.reason());
  }
}
