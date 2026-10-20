package com.pawcycle.backend.commerce.delivery.api;

import com.pawcycle.backend.commerce.delivery.application.DeliveryResult;

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
    Timestamp cancelledAt) {
  public static DeliveryResponse from(DeliveryResult v) {
    return new DeliveryResponse(v.deliveryId(), v.orderId(), v.status(), v.carrierCode(), v.trackingNumber(), v.failureReason(), v.shippedAt(), v.deliveredAt(), v.failedAt(), v.cancelledAt());
  }
}
