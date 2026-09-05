package com.pawcycle.backend.commerce.refund.api;

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
    Timestamp completedAt) {}
