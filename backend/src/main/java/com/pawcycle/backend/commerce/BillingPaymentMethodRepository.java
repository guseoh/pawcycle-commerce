package com.pawcycle.backend.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPaymentMethodRepository
    extends JpaRepository<BillingPaymentMethodEntity, Long> {}
