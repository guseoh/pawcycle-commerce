package com.pawcycle.backend.commerce.checkout.application;

import java.math.BigDecimal;

/** Typed application pricing; API JSON contracts remain in the Controller. */
public record CheckoutPricing(
    BigDecimal originalAmount,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal shippingFee,
    BigDecimal finalAmount,
    BigDecimal paymentAmount) {}
