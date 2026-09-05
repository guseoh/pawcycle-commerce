package com.pawcycle.backend.commerce.cart.persistence;

import java.math.BigDecimal;

public record CartItemView(
    long skuId,
    int quantity,
    String skuCode,
    String skuName,
    BigDecimal price,
    BigDecimal unitPrice,
    BigDecimal lineAmount,
    long productId,
    String productName,
    int availableQuantity,
    boolean purchasable) {}
