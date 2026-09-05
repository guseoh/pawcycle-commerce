package com.pawcycle.backend.commerce.notification.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.sql.Timestamp;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
    long notificationId,
    String type,
    String referenceType,
    long referenceId,
    Timestamp readAt,
    Timestamp createdAt,
    Long subscriptionId,
    Timestamp scheduledDate) {}
