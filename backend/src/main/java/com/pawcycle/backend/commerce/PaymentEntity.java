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
@Table(name = "payments")
class PaymentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false)
  Long orderId;

  @Column(nullable = false, length = 20)
  String type;

  @Column(nullable = false, length = 20)
  String provider;

  @Column(nullable = false, length = 20)
  String status;

  @Column(nullable = false, precision = 18, scale = 2)
  BigDecimal amount;

  @Column(name = "provider_order_id", nullable = false, unique = true, length = 100)
  String providerOrderId;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
  String idempotencyKey;

  @Column(name = "attempt_no", nullable = false)
  int attemptNo;

  @Column(name = "reconciliation_attempts", nullable = false)
  int reconciliationAttempts;

  @Column(name = "succeeded_order_id", insertable = false, updatable = false)
  Long succeededOrderId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", insertable = false, updatable = false)
  CommerceOrderEntity order;
}
