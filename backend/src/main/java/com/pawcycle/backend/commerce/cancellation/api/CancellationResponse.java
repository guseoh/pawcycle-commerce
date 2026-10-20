package com.pawcycle.backend.commerce.cancellation.api;

import com.pawcycle.backend.commerce.cancellation.application.CancellationResult;

import java.sql.Timestamp;

public record CancellationResponse(
    long cancellationId,
    String status,
    String reason,
    Timestamp requestedAt,
    Timestamp completedAt) {
  public static CancellationResponse from(CancellationResult v) {
    return new CancellationResponse(v.cancellationId(), v.status(), v.reason(), v.requestedAt(), v.completedAt());
  }
}
