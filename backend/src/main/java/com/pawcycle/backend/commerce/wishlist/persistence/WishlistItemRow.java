package com.pawcycle.backend.commerce.wishlist.persistence;

import java.time.LocalDateTime;

record WishlistItemRow(long productId, String productName, LocalDateTime createdAt) {
  WishlistItemView toView() {
    return new WishlistItemView(productId, productName, java.sql.Timestamp.valueOf(createdAt));
  }
}
