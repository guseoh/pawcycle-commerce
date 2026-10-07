package com.pawcycle.backend.commerce.checkout.api;

import com.pawcycle.backend.commerce.checkout.application.CheckoutIdempotencyService;
import com.pawcycle.backend.commerce.checkout.api.CheckoutRequest;
import com.pawcycle.backend.commerce.payment.infrastructure.toss.TossPaymentAdapter;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
  private final CheckoutIdempotencyService checkout;
  private final TossPaymentAdapter payment;

  public CheckoutController(CheckoutIdempotencyService checkout, TossPaymentAdapter payment) {
    this.checkout = checkout;
    this.payment = payment;
  }

  @PostMapping
  public CheckoutResponse checkout(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CheckoutRequest request) {
    CheckoutResponse result =
        checkout.checkout(
            principal.memberId(),
            idempotencyKey,
            request.addressId(),
            request.memberCouponId(),
            request.cartVersion());
    return result.withTossTestEnabled(payment.browserTestEnabled());
  }
}
