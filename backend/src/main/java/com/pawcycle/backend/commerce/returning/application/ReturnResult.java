package com.pawcycle.backend.commerce.returning.application;

import java.sql.Timestamp;

public record ReturnResult(
    long returnId,
    String status,
    String reason,
    String rejectionReason,
    Boolean restock,
    Timestamp requestedAt,
    Timestamp decidedAt,
    Timestamp receivedAt,
    Timestamp completedAt) {}
