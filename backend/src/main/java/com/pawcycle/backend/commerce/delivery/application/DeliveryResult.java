package com.pawcycle.backend.commerce.delivery.application;

import java.sql.Timestamp;

public record DeliveryResult(
    long deliveryId,
    long orderId,
    String status,
    String carrierCode,
    String trackingNumber,
    String failureReason,
    Timestamp shippedAt,
    Timestamp deliveredAt,
    Timestamp failedAt,
    Timestamp cancelledAt) {}
