package com.pawcycle.backend.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistItemRepository extends JpaRepository<WishlistItemEntity, WishlistItemId> {}
