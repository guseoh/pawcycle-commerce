package com.pawcycle.backend.commerce.inventory.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovementEntity, Long> {}