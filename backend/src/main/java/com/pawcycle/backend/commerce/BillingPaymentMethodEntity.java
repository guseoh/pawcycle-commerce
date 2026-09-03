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
@Table(name = "billing_payment_methods")
class BillingPaymentMethodEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(nullable = false, length = 20)
  String provider;

  @Column(name = "customer_key", nullable = false, unique = true, length = 100)
  String customerKey;

  @Column(name = "billing_key", nullable = false, length = 300)
  String billingKey;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "active_member_id", insertable = false, updatable = false)
  Long activeMemberId;
}
