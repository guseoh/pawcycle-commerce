package com.pawcycle.backend.commerce;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
	Optional<PaymentEntity> findByProviderOrderId(String providerOrderId);
}
