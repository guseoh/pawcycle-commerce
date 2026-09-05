package com.pawcycle.backend.commerce.checkout.persistence;

import java.math.BigDecimal;

public record CheckoutOrderPricing(
    BigDecimal originalAmount,
    BigDecimal discountAmount,
    BigDecimal shippingFee,
    BigDecimal paymentAmount) {}
