package com.pawcycle.backend.commerce;

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
@Table(name = "billing_payment_methods")
public class BillingPaymentMethodEntity {
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

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;

  @Column(name = "revoked_at")
  LocalDateTime revokedAt;

  protected BillingPaymentMethodEntity() {}

  public BillingPaymentMethodEntity(
      long memberId, String customerKey, String billingKey, LocalDateTime createdAt) {
    this.memberId = memberId;
    this.provider = "TOSS";
    this.customerKey = customerKey;
    this.billingKey = billingKey;
    this.status = "ACTIVE";
    this.createdAt = createdAt;
  }
}
