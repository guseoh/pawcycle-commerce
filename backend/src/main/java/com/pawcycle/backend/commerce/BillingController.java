package com.pawcycle.backend.commerce;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class BillingController {
  private final CommerceService commerce;
  private final TossPaymentAdapter provider;
  private final BillingMethodQueryService billingMethods;

  BillingController(
      CommerceService commerce,
      TossPaymentAdapter provider,
      BillingMethodQueryService billingMethods) {
    this.commerce = commerce;
    this.provider = provider;
    this.billingMethods = billingMethods;
  }

  @GetMapping("/payment-capabilities")
  PaymentCapabilitiesResponse capabilities() {
    return new PaymentCapabilitiesResponse(provider.isConfigured() ? "SANDBOX" : "UNAVAILABLE");
  }

  @GetMapping("/payment-methods/toss/billing")
  BillingMethodResponse billing(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) {
    return billingMethods.active(p.memberId());
  }

  @PostMapping("/payment-methods/toss/billing/prepare")
  BillingPreparationResponse prepare(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) {
    return commerce.prepareBilling(p.memberId());
  }

  @PostMapping("/payment-methods/toss/billing/complete")
  ResponseEntity<Void> complete(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal p,
      @Valid @RequestBody BillingCompleteRequest r) {
    commerce.completeBilling(p.memberId(), r.prepareToken(), r.authKey());
    return ResponseEntity.noContent().build();
  }
}
