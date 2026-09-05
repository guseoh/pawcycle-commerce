package com.pawcycle.backend.commerce.cart.persistence;

import java.math.BigDecimal;

record CartItemRow(
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
    boolean purchasable) {
  CartItemView toView() {
    return new CartItemView(
        skuId,
        quantity,
        skuCode,
        skuName,
        price,
        unitPrice,
        lineAmount,
        productId,
        productName,
        availableQuantity,
        purchasable);
  }
}
