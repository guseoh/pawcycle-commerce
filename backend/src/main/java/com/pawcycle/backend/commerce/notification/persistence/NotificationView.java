package com.pawcycle.backend.commerce.notification.persistence;

import java.sql.Timestamp;

public record NotificationView(
    long notificationId,
    String type,
    String referenceType,
    long referenceId,
    Timestamp readAt,
    Timestamp createdAt,
    Long subscriptionId,
    Timestamp scheduledDate) {}
