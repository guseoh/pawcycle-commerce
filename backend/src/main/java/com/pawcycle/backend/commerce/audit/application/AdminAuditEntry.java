package com.pawcycle.backend.commerce.audit.application;

import java.sql.Timestamp;

/** Typed application projection without an HTTP dependency. */
public record AdminAuditEntry(
    long auditLogId, long adminId, String action, String targetType, long targetId,
    Timestamp createdAt) {}
