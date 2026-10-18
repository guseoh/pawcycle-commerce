package com.pawcycle.backend.commerce.cart.api;

import com.pawcycle.backend.commerce.cart.model.CartItemView;
import java.math.BigDecimal;

public record CartItemResponse(
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
  public static CartItemResponse from(CartItemView view) {
    return new CartItemResponse(
        view.skuId(), view.quantity(), view.skuCode(), view.skuName(), view.price(),
        view.unitPrice(), view.lineAmount(), view.productId(), view.productName(),
        view.availableQuantity(), view.purchasable());
  }

}
