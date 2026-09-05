package com.pawcycle.backend.commerce.audit.api;

import java.sql.Timestamp;

public record AdminAuditResponse(
    long auditLogId,
    long adminId,
    String action,
    String targetType,
    long targetId,
    Timestamp createdAt) {}
