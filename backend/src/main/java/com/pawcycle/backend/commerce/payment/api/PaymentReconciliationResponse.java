package com.pawcycle.backend.commerce.payment.api;

import com.pawcycle.backend.commerce.payment.application.PaymentReconciliationResult;

import java.sql.Timestamp;

public record PaymentReconciliationResponse(
    long paymentId,
    long orderId,
    String status,
    int reconciliationAttempts,
    Timestamp lastReconciledAt) {
  public static PaymentReconciliationResponse from(PaymentReconciliationResult model) {
    return new PaymentReconciliationResponse(model.paymentId(), model.orderId(), model.status(), model.reconciliationAttempts(), model.lastReconciledAt());
  }
}
