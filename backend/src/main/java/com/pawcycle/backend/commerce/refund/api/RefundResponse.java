package com.pawcycle.backend.commerce.refund.api;

import com.pawcycle.backend.commerce.refund.application.RefundResult;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record RefundResponse(
    long refundId,
    long orderId,
    String source,
    String status,
    BigDecimal amount,
    int attemptNo,
    int reconciliationAttempts,
    String providerStatus,
    String failureCode,
    Timestamp requestedAt,
    Timestamp processedAt,
    Timestamp completedAt) {
  public static RefundResponse from(RefundResult v) {
    return new RefundResponse(v.refundId(), v.orderId(), v.source(), v.status(), v.amount(), v.attemptNo(), v.reconciliationAttempts(), v.providerStatus(), v.failureCode(), v.requestedAt(), v.processedAt(), v.completedAt());
  }
}
