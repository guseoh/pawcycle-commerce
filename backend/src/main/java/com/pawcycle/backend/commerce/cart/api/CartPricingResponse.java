package com.pawcycle.backend.commerce.cart.api;

import java.math.BigDecimal;

public record CartPricingResponse(
    BigDecimal originalAmount,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal shippingFee,
    BigDecimal finalAmount,
    BigDecimal paymentAmount) {}
