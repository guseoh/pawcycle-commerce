package com.pawcycle.backend.commerce.checkout.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckoutResponse(
    long orderId,
    String orderNumber,
    long paymentId,
    String providerOrderId,
    String orderName,
    BigDecimal amount,
    CheckoutPricingResponse pricing,
    Boolean tossTestEnabled) {
  public CheckoutResponse(
      long orderId,
      String orderNumber,
      long paymentId,
      String providerOrderId,
      String orderName,
      BigDecimal amount,
      CheckoutPricingResponse pricing) {
    this(orderId, orderNumber, paymentId, providerOrderId, orderName, amount, pricing, null);
  }

  public CheckoutResponse withTossTestEnabled(boolean enabled) {
    return new CheckoutResponse(
        orderId,
        orderNumber,
        paymentId,
        providerOrderId,
        orderName,
        amount,
        pricing,
        enabled);
  }
}
