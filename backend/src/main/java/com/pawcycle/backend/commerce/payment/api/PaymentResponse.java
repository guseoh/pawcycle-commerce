package com.pawcycle.backend.commerce.payment.api;

import com.pawcycle.backend.commerce.payment.application.PaymentResult;

public record PaymentResponse(long paymentId, long orderId, String status) {
  public static PaymentResponse from(PaymentResult model) {
    return new PaymentResponse(model.paymentId(), model.orderId(), model.status());
  }
}
