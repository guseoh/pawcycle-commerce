package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "checkout_idempotency_results")
public class CheckoutIdempotencyEntity {
  @EmbeddedId CheckoutIdempotencyId id;

  @Column(name = "order_id", nullable = false)
  Long orderId;

  @Column(name = "payment_id", nullable = false)
  Long paymentId;

  @Column(name = "request_fingerprint", length = 64)
  String requestFingerprint;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;

  @Column(name = "request_cart_version")
  Long requestCartVersion;

  protected CheckoutIdempotencyEntity() {}

  public CheckoutIdempotencyEntity(
      CheckoutIdempotencyId id,
      long orderId,
      long paymentId,
      String requestFingerprint,
      LocalDateTime createdAt) {
    this.id = id;
    this.orderId = orderId;
    this.paymentId = paymentId;
    this.requestFingerprint = requestFingerprint;
    this.createdAt = createdAt;
  }

  public CheckoutIdempotencyId getId() {
    return id;
  }

  public long getOrderId() {
    return orderId;
  }

  public long getPaymentId() {
    return paymentId;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public Long getRequestCartVersion() {
    return requestCartVersion;
  }
}
