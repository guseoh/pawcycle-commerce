package com.pawcycle.backend.commerce.wishlist.api;

import java.util.List;

public record WishlistResponse(List<WishlistItemResponse> items) {
  public WishlistResponse {
    items = List.copyOf(items);
  }
}
