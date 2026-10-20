package com.pawcycle.backend.commerce.cancellation.application;

import java.sql.Timestamp;

public record CancellationResult(
    long cancellationId,
    String status,
    String reason,
    Timestamp requestedAt,
    Timestamp completedAt) {}
