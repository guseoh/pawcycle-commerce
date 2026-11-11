package com.pawcycle.backend.commerce.refund.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "refunds")
class RefundEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false)
  Long orderId;

  @Column(nullable = false, length = 20)
  String source;

  @Column(nullable = false, length = 20)
  String status;

  @Column(nullable = false, precision = 18, scale = 2)
  BigDecimal amount;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
  String idempotencyKey;

  @Column(name = "attempt_no", nullable = false)
  int attemptNo;

  @Column(name = "reconciliation_attempts", nullable = false, insertable = false, updatable = false)
  int reconciliationAttempts;

  @Column(name = "source_id", insertable = false, updatable = false)
  Long sourceId;

  @Column(name = "succeeded_order_id", insertable = false, updatable = false)
  Long succeededOrderId;
}
