package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "billing_payment_method_preparations")
public class BillingPaymentMethodPreparationEntity {
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

  protected BillingPaymentMethodPreparationEntity() {}

  public BillingPaymentMethodPreparationEntity(
      String prepareToken, long memberId, String customerKey, LocalDateTime expiresAt) {
    this.prepareToken = prepareToken;
    this.memberId = memberId;
    this.customerKey = customerKey;
    this.status = "READY";
    this.expiresAt = expiresAt;
  }

  public String getCustomerKey() {
    return customerKey;
  }

  public String getStatus() {
    return status;
  }

  public void claim(LocalDateTime claimedAt) {
    status = "PROCESSING";
    this.claimedAt = claimedAt;
  }
}
