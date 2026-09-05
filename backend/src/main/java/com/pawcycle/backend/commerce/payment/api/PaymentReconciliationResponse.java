package com.pawcycle.backend.commerce.payment.api;

import java.sql.Timestamp;

public record PaymentReconciliationResponse(
    long paymentId,
    long orderId,
    String status,
    int reconciliationAttempts,
    Timestamp lastReconciledAt) {}
