package com.pawcycle.backend.commerce.cart.api;

import com.pawcycle.backend.commerce.cart.model.CartResult;
import java.util.List;

public record CartResponse(
    List<CartItemResponse> items, long version, CartPricingResponse pricing) {
  public static CartResponse from(CartResult view) {
    return new CartResponse(
        view.cart().items().stream().map(CartItemResponse::from).toList(),
        view.cart().version(), CartPricingResponse.from(view.pricing()));
  }

  public CartResponse {
    items = List.copyOf(items);
  }
}
