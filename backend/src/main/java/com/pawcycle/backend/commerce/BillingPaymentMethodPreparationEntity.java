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
@Table(name = "billing_payment_method_preparations")
class BillingPaymentMethodPreparationEntity {
  @Id
  @Column(name = "prepare_token", length = 100)
  String prepareToken;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(name = "customer_key", nullable = false, length = 100)
  String customerKey;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "expires_at", nullable = false)
  LocalDateTime expiresAt;

  @Column(name = "claimed_at")
  LocalDateTime claimedAt;
}
