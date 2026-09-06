package com.pawcycle.backend.commerce.checkout.persistence;

import java.math.BigDecimal;

record CheckoutCartItemRow(
    long skuId,
    int quantity,
    String skuCode,
    String skuName,
    BigDecimal price,
    String productName) {
  CheckoutCartItem toView() {
    return new CheckoutCartItem(skuId, quantity, skuCode, skuName, price, productName);
  }
}
