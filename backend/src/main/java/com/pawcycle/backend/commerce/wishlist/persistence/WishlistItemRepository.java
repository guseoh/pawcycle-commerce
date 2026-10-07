package com.pawcycle.backend.commerce.wishlist.persistence;

import com.pawcycle.backend.commerce.wishlist.domain.WishlistItemEntity;
import com.pawcycle.backend.commerce.wishlist.domain.WishlistItemId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistItemRepository extends JpaRepository<WishlistItemEntity, WishlistItemId> {}
