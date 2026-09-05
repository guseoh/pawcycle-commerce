package com.pawcycle.backend.commerce.checkout.persistence;

import java.math.BigDecimal;

public record CheckoutReplay(
    String requestFingerprint,
    Long requestCartVersion,
    long orderId,
    String orderNumber,
    long paymentId,
    String providerOrderId,
    BigDecimal amount) {}
