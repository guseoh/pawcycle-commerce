package com.pawcycle.backend.commerce.delivery.persistence;

import java.sql.Timestamp;

public record DeliveryView(
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
