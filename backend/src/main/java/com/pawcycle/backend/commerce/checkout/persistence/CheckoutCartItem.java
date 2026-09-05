package com.pawcycle.backend.commerce.checkout.persistence;

import java.math.BigDecimal;

public record CheckoutCartItem(
    long skuId,
    int quantity,
    String skuCode,
    String skuName,
    BigDecimal price,
    String productName) {}
