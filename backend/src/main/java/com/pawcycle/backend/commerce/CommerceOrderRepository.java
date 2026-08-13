package com.pawcycle.backend.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceOrderRepository extends JpaRepository<CommerceOrderEntity, Long> {
}
