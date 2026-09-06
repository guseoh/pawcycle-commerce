package com.pawcycle.backend.commerce.wishlist.persistence;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

record WishlistItemRow(long productId, String productName, LocalDateTime createdAt) {
  WishlistItemView toView() {
    return new WishlistItemView(
        productId, productName, Timestamp.from(createdAt.toInstant(ZoneOffset.UTC)));
  }
}
