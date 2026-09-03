package com.pawcycle.backend.subscription;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class RepeatCommerceController {
  private final RepeatCommerceService service;

  RepeatCommerceController(RepeatCommerceService service) {
    this.service = service;
  }

  @GetMapping("/recommendations/reorder-timing")
  com.pawcycle.backend.commerce.CommercePayload reorder(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return service.reorderTiming(principal.memberId());
  }

  @GetMapping("/subscriptions/{subscriptionId}/cycle-suggestion")
  com.pawcycle.backend.commerce.CommercePayload cycle(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long subscriptionId) {
    return service.cycleSuggestion(principal.memberId(), subscriptionId);
  }

  @GetMapping("/orders/{orderId}/subscription-options")
  com.pawcycle.backend.commerce.CommercePayload options(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long orderId) {
    return service.subscriptionOptions(principal.memberId(), orderId);
  }
}
