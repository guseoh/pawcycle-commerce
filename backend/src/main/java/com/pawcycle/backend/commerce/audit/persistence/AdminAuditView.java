package com.pawcycle.backend.commerce.audit.persistence;

import java.sql.Timestamp;

public record AdminAuditView(
    long auditLogId,
    long adminId,
    String action,
    String targetType,
    long targetId,
    Timestamp createdAt) {}
