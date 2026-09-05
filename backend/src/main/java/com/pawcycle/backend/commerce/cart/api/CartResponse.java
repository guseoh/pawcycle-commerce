package com.pawcycle.backend.commerce.cart.api;

import java.util.List;

public record CartResponse(
    List<CartItemResponse> items, long version, CartPricingResponse pricing) {
  public CartResponse {
    items = List.copyOf(items);
  }
}
