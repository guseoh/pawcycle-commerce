package com.pawcycle.backend.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPaymentMethodPreparationRepository
    extends JpaRepository<BillingPaymentMethodPreparationEntity, String> {}
