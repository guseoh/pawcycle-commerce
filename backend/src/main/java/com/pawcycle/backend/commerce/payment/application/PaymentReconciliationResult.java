package com.pawcycle.backend.commerce.payment.application;

import java.sql.Timestamp;

public record PaymentReconciliationResult(
    long paymentId,
    long orderId,
    String status,
    int reconciliationAttempts,
    Timestamp lastReconciledAt) {}
