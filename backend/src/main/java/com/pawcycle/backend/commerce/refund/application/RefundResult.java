package com.pawcycle.backend.commerce.refund.application;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record RefundResult(
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
    Timestamp completedAt) {}
