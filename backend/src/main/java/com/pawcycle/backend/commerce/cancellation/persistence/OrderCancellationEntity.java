package com.pawcycle.backend.commerce.cancellation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "order_cancellations")
class OrderCancellationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  Long orderId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(nullable = false, length = 500)
  String reason;

  @Column(name = "requested_at", nullable = false)
  LocalDateTime requestedAt;

  @Column(name = "completed_at", insertable = false, updatable = false)
  LocalDateTime completedAt;
}
