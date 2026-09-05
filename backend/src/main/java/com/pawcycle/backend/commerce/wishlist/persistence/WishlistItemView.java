package com.pawcycle.backend.commerce.wishlist.persistence;

import java.sql.Timestamp;

public record WishlistItemView(long productId, String productName, Timestamp createdAt) {}
