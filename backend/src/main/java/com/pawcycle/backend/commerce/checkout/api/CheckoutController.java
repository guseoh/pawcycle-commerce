package com.pawcycle.backend.commerce.checkout.api;

import com.pawcycle.backend.commerce.CheckoutIdempotencyService;
import com.pawcycle.backend.commerce.CheckoutRequest;
import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.commerce.TossPaymentAdapter;
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
  public CommercePayload checkout(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CheckoutRequest request) {
    CommercePayload result =
        checkout.checkout(
            principal.memberId(),
            idempotencyKey,
            request.addressId(),
            request.memberCouponId(),
            request.cartVersion());
    result.put("tossTestEnabled", payment.browserTestEnabled());
    return result;
  }
}
