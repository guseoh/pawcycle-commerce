package com.pawcycle.backend.commerce.wishlist.api;

import com.pawcycle.backend.commerce.wishlist.model.WishlistItemView;
import java.sql.Timestamp;

public record WishlistItemResponse(long productId, String productName, Timestamp createdAt) {
  public static WishlistItemResponse from(WishlistItemView view) {
    return new WishlistItemResponse(view.productId(), view.productName(), view.createdAt());
  }

}
