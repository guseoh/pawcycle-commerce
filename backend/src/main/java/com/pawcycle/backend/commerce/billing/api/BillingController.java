package com.pawcycle.backend.commerce.billing.api;

import com.pawcycle.backend.commerce.BillingCompleteRequest;
import com.pawcycle.backend.commerce.BillingMethodQueryService;
import com.pawcycle.backend.commerce.BillingMethodResponse;
import com.pawcycle.backend.commerce.BillingPreparationResponse;
import com.pawcycle.backend.commerce.PaymentCapabilitiesResponse;
import com.pawcycle.backend.commerce.TossPaymentAdapter;
import com.pawcycle.backend.commerce.billing.application.BillingApplicationService;
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
public class BillingController {
  private final BillingApplicationService billing;
  private final TossPaymentAdapter provider;
  private final BillingMethodQueryService billingMethods;

  public BillingController(
      BillingApplicationService billing,
      TossPaymentAdapter provider,
      BillingMethodQueryService billingMethods) {
    this.billing = billing;
    this.provider = provider;
    this.billingMethods = billingMethods;
  }

  @GetMapping("/payment-capabilities")
  public PaymentCapabilitiesResponse capabilities() {
    return new PaymentCapabilitiesResponse(provider.isConfigured() ? "SANDBOX" : "UNAVAILABLE");
  }

  @GetMapping("/payment-methods/toss/billing")
  public BillingMethodResponse billing(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return billingMethods.active(principal.memberId());
  }

  @PostMapping("/payment-methods/toss/billing/prepare")
  public BillingPreparationResponse prepare(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
    return billing.prepare(principal.memberId());
  }

  @PostMapping("/payment-methods/toss/billing/complete")
  public ResponseEntity<Void> complete(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody BillingCompleteRequest request) {
    billing.complete(principal.memberId(), request.prepareToken(), request.authKey());
    return ResponseEntity.noContent().build();
  }
}
