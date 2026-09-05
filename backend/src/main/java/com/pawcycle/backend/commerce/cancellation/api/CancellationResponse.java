package com.pawcycle.backend.commerce.cancellation.api;

import java.sql.Timestamp;

public record CancellationResponse(
    long cancellationId,
    String status,
    String reason,
    Timestamp requestedAt,
    Timestamp completedAt) {}
