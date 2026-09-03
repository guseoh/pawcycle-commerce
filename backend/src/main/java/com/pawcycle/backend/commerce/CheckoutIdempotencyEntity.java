package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "checkout_idempotency_results")
class CheckoutIdempotencyEntity {
  @EmbeddedId CheckoutIdempotencyId id;

  @Column(name = "order_id", nullable = false)
  Long orderId;

  @Column(name = "payment_id", nullable = false)
  Long paymentId;

  @Column(name = "request_fingerprint", length = 64)
  String requestFingerprint;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;
}
