package com.pawcycle.backend.commerce.cart.api;

import com.pawcycle.backend.commerce.cart.model.CartPricingView;
import java.math.BigDecimal;

public record CartPricingResponse(
    BigDecimal originalAmount,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal shippingFee,
    BigDecimal finalAmount,
    BigDecimal paymentAmount) {
  public static CartPricingResponse from(CartPricingView view) {
    return new CartPricingResponse(
        view.originalAmount(), view.subtotalAmount(), view.discountAmount(), view.shippingFee(),
        view.finalAmount(), view.paymentAmount());
  }

}
