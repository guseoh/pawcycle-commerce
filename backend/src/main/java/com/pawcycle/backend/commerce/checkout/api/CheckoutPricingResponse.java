package com.pawcycle.backend.commerce.checkout.api;

import java.math.BigDecimal;

public record CheckoutPricingResponse(
    BigDecimal originalAmount,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal shippingFee,
    BigDecimal finalAmount,
    BigDecimal paymentAmount) {}
