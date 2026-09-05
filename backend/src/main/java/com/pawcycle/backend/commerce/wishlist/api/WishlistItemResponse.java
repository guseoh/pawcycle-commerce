package com.pawcycle.backend.commerce.wishlist.api;

import java.sql.Timestamp;

public record WishlistItemResponse(long productId, String productName, Timestamp createdAt) {}
