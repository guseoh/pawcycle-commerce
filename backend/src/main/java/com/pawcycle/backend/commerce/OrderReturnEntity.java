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
@Table(name = "order_returns")
class OrderReturnEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  Long orderId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(nullable = false, length = 500)
  String reason;

  @Column(name = "rejection_reason", length = 500)
  String rejectionReason;

  @Column(name = "requested_at", nullable = false)
  LocalDateTime requestedAt;
}
