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
@Table(name = "orders")
class CommerceOrderEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "order_number", nullable = false, unique = true, length = 80)
  String orderNumber;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(nullable = false, length = 20)
  String source;

  @Column(nullable = false, length = 30)
  String status;

  @Column(name = "payment_amount", nullable = false, precision = 18, scale = 2)
  BigDecimal paymentAmount;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;
}
