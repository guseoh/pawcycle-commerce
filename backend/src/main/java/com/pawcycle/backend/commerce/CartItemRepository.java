package com.pawcycle.backend.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItemEntity, CartItemId> {}
