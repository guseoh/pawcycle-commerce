package com.pawcycle.backend.commerce.cart.model;

import java.math.BigDecimal;

/** Pricing calculated by the cart use case, without HTTP dependencies. */
public record CartPricingView(
    BigDecimal originalAmount,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal shippingFee,
    BigDecimal finalAmount,
    BigDecimal paymentAmount) {}
