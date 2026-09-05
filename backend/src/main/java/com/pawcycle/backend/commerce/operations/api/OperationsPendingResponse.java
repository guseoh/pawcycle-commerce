package com.pawcycle.backend.commerce.operations.api;

import java.sql.Timestamp;
import java.util.List;

public record OperationsPendingResponse(
    String type, long referenceId, Timestamp createdAt, Integer attemptNo, List<String> availableActions) {}
