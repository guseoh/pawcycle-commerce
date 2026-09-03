package com.pawcycle.backend.commerce.payment.api;

import com.pawcycle.backend.commerce.BillingRetryResponse;
import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.commerce.PaymentReconciliationService;
import com.pawcycle.backend.commerce.SubscriptionBillingService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {
  private final PaymentReconciliationService payments;
  private final SubscriptionBillingService billing;

  public AdminPaymentController(
      PaymentReconciliationService payments, SubscriptionBillingService billing) {
    this.payments = payments;
    this.billing = billing;
  }

  @PostMapping("/{id}/reconcile")
  public CommercePayload reconcile(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long id) {
    return payments.reconcile(id, principal.memberId());
  }

  @PostMapping("/{id}/retry-billing")
  public BillingRetryResponse retryBilling(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long id) {
    long next = billing.retryHeldBilling(id, principal.memberId());
    return new BillingRetryResponse(id, next, next == 0 ? "HELD" : "READY");
  }
}
