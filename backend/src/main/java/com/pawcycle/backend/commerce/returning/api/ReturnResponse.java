package com.pawcycle.backend.commerce.returning.api;

import java.sql.Timestamp;

public record ReturnResponse(
    long returnId,
    String status,
    String reason,
    String rejectionReason,
    Boolean restock,
    Timestamp requestedAt,
    Timestamp decidedAt,
    Timestamp receivedAt,
    Timestamp completedAt) {}
