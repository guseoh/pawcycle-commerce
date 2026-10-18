package com.pawcycle.backend.commerce.wishlist.api;

import com.pawcycle.backend.commerce.wishlist.model.WishlistItemView;
import java.util.List;

public record WishlistResponse(List<WishlistItemResponse> items) {
  public static WishlistResponse from(List<WishlistItemView> views) {
    return new WishlistResponse(views.stream().map(WishlistItemResponse::from).toList());
  }

  public WishlistResponse {
    items = List.copyOf(items);
  }
}
