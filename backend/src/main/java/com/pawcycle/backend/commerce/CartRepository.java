package com.pawcycle.backend.commerce;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<CartEntity, Long> {
	Optional<CartEntity> findByMemberId(long memberId);
}
