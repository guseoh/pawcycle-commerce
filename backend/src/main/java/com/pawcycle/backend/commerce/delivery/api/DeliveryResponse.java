package com.pawcycle.backend.commerce.delivery.api;

import java.sql.Timestamp;

public record DeliveryResponse(
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
