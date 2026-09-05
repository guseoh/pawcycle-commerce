package com.pawcycle.backend.subscription;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.subscription.api.RepeatPurchaseResponse;
import com.pawcycle.backend.subscription.api.SubscriptionCycleSuggestionResponse;
import com.pawcycle.backend.subscription.api.SubscriptionOptionsResponse;
import com.pawcycle.backend.subscription.application.RepeatPurchaseApplicationService;
import com.pawcycle.backend.subscription.application.SubscriptionConversionApplicationService;
import com.pawcycle.backend.subscription.application.SubscriptionCycleSuggestionApplicationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class RepeatCommerceController {
  private final RepeatPurchaseApplicationService repeatPurchase;
  private final SubscriptionCycleSuggestionApplicationService cycleSuggestion;
  private final SubscriptionConversionApplicationService conversion;

  RepeatCommerceController(
      RepeatPurchaseApplicationService repeatPurchase,
      SubscriptionCycleSuggestionApplicationService cycleSuggestion,
      SubscriptionConversionApplicationService conversion) {
    this.repeatPurchase = repeatPurchase;
    this.cycleSuggestion = cycleSuggestion;
    this.conversion = conversion;
  }

  @GetMapping("/recommendations/reorder-timing")
  RepeatPurchaseResponse reorder(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return repeatPurchase.reorderTiming(principal.memberId());
  }

  @GetMapping("/subscriptions/{subscriptionId}/cycle-suggestion")
  SubscriptionCycleSuggestionResponse cycle(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long subscriptionId) {
    return cycleSuggestion.cycleSuggestion(principal.memberId(), subscriptionId);
  }

  @GetMapping("/orders/{orderId}/subscription-options")
  SubscriptionOptionsResponse options(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long orderId) {
    return conversion.subscriptionOptions(principal.memberId(), orderId);
  }
}
