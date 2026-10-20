package com.pawcycle.backend.commerce.operations.application;

import java.sql.Timestamp;
import java.util.List;

/** Approved available actions remain a use-case decision, independent from HTTP DTOs. */
public record OperationsPendingView(
    String type, long referenceId, Timestamp createdAt, Integer attemptNo,
    List<String> availableActions) {}
