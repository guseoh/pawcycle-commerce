package com.pawcycle.backend.commerce.notification.application;

import java.sql.Timestamp;

/** Notification read projection; response JSON inclusion is owned by the API layer. */
public record NotificationEntry(
    long notificationId, String type, String referenceType, long referenceId,
    Timestamp readAt, Timestamp createdAt, Long subscriptionId, Timestamp scheduledDate) {}
