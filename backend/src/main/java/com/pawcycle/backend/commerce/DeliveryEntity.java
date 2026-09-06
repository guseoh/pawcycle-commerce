package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "deliveries")
@Getter
public class DeliveryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  Long orderId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "carrier_code", length = 50)
  String carrierCode;

  @Column(name = "tracking_number", length = 100)
  String trackingNumber;

  @Column(name = "failure_reason", length = 500)
  String failureReason;

  @Column(name = "shipped_at")
  LocalDateTime shippedAt;

  @Column(name = "delivered_at")
  LocalDateTime deliveredAt;

  @Column(name = "failed_at")
  LocalDateTime failedAt;

  @Column(name = "cancelled_at")
  LocalDateTime cancelledAt;

  protected DeliveryEntity() {}

  public DeliveryEntity(long orderId, LocalDateTime now) {
    this.orderId = orderId;
    this.status = "PREPARING";
  }

  public void ship(String carrier, String tracking, LocalDateTime now) {
    status = "SHIPPED";
    carrierCode = carrier;
    trackingNumber = tracking;
    failureReason = null;
    failedAt = null;
    shippedAt = now;
  }

  public void transition(String to, String failureReason, LocalDateTime now) {
    status = to;
    this.failureReason = failureReason;
    if ("DELIVERED".equals(to)) deliveredAt = now;
    if ("FAILED".equals(to)) failedAt = now;
  }
}
