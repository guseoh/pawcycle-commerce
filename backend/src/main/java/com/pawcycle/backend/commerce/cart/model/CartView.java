package com.pawcycle.backend.commerce.cart.model;

import java.util.List;

public record CartView(List<CartItemView> items, long version) {
  public CartView {
    items = List.copyOf(items);
  }
}
