package com.pawcycle.backend.commerce.checkout.application;

import java.math.BigDecimal;

/** Shared outcome of Checkout creation and idempotency replay. */
public record CheckoutResult(
    long orderId,
    String orderNumber,
    long paymentId,
    String providerOrderId,
    String orderName,
    BigDecimal amount,
    CheckoutPricing pricing) {}
