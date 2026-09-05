package com.pawcycle.backend.commerce.cart.persistence;

import java.util.List;

public record CartView(List<CartItemView> items, long version) {
  public CartView {
    items = List.copyOf(items);
  }
}
