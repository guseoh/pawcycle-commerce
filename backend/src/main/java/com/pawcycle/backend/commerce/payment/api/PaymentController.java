package com.pawcycle.backend.commerce.payment.api;

import com.pawcycle.backend.commerce.PaymentConfirmRequest;
import com.pawcycle.backend.commerce.payment.application.PaymentApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/toss")
public class PaymentController {
  private final PaymentApplicationService payment;

  public PaymentController(PaymentApplicationService payment) {
    this.payment = payment;
  }

  @PostMapping("/confirm")
  public PaymentResponse confirm(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody PaymentConfirmRequest request) {
    return payment.confirm(
        principal.memberId(), request.paymentKey(), request.providerOrderId(), request.amount());
  }
}
