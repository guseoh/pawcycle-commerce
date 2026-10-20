package com.pawcycle.backend.commerce.returning.api;

import com.pawcycle.backend.commerce.returning.application.ReturnResult;

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
    Timestamp completedAt) {
  public static ReturnResponse from(ReturnResult v) {
    return new ReturnResponse(v.returnId(), v.status(), v.reason(), v.rejectionReason(), v.restock(), v.requestedAt(), v.decidedAt(), v.receivedAt(), v.completedAt());
  }
}
