package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "payments")
public class PaymentEntity {
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

  @Column(name = "provider_status", length = 100)
  String providerStatus;

  @Column(nullable = false, precision = 18, scale = 2)
  BigDecimal amount;

  @Column(name = "provider_order_id", nullable = false, unique = true, length = 100)
  String providerOrderId;

  @Column(name = "payment_key", length = 200)
  String paymentKey;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
  String idempotencyKey;

  @Column(name = "attempt_no", nullable = false)
  int attemptNo;

  @Column(name = "failure_code", length = 100)
  String failureCode;

  @Column(name = "failure_message", length = 500)
  String failureMessage;

  @Column(name = "requested_at", nullable = false)
  LocalDateTime requestedAt;

  @Column(name = "approved_at")
  LocalDateTime approvedAt;

  @Column(name = "failed_at")
  LocalDateTime failedAt;

  @Column(name = "expires_at")
  LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  LocalDateTime createdAt;

  @Column(name = "reconciliation_attempts", nullable = false)
  int reconciliationAttempts;

  @Column(name = "last_reconciled_at")
  LocalDateTime lastReconciledAt;

  @Column(name = "succeeded_order_id", insertable = false, updatable = false)
  Long succeededOrderId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", insertable = false, updatable = false)
  CommerceOrderEntity order;

  protected PaymentEntity() {}

  public PaymentEntity(
      long orderId,
      BigDecimal amount,
      String providerOrderId,
      String idempotencyKey,
      LocalDateTime now,
      LocalDateTime expiresAt) {
    this.orderId = orderId;
    this.type = "NORMAL";
    this.provider = "TOSS";
    this.status = "READY";
    this.amount = amount;
    this.providerOrderId = providerOrderId;
    this.idempotencyKey = idempotencyKey;
    this.attemptNo = 1;
    this.reconciliationAttempts = 0;
    this.requestedAt = now;
    this.expiresAt = expiresAt;
    this.createdAt = now;
  }

  public Long getId() {
    return id;
  }

  public long getOrderId() {
    return orderId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getStatus() {
    return status;
  }

  public String getPaymentKey() {
    return paymentKey;
  }

  public String getProviderOrderId() {
    return providerOrderId;
  }

  public String getProviderStatus() {
    return providerStatus;
  }

  public int getReconciliationAttempts() {
    return reconciliationAttempts;
  }

  public LocalDateTime getLastReconciledAt() {
    return lastReconciledAt;
  }

  public CommerceOrderEntity getOrder() {
    return order;
  }

  public void markProcessing(String paymentKey) {
    status = "PROCESSING";
    this.paymentKey = paymentKey;
    providerStatus = "REQUESTED";
  }

  public void markSucceeded(String paymentKey, LocalDateTime now) {
    status = "SUCCEEDED";
    providerStatus = "DONE";
    this.paymentKey = paymentKey;
    approvedAt = now;
  }

  public void markFailed(LocalDateTime now) {
    status = "FAILED";
    providerStatus = "ABORTED";
    failureCode = "TOSS_REJECTED";
    failedAt = now;
  }

  public void markUnknown() {
    status = "UNKNOWN";
    providerStatus = "UNKNOWN";
    failureCode = "PROVIDER_RESULT_UNKNOWN";
  }

  public void markExpired(LocalDateTime now) {
    status = "FAILED";
    providerStatus = "EXPIRED";
    failureCode = "CHECKOUT_EXPIRED";
    failedAt = now;
  }

  public void updateReconciliation(int attempts, LocalDateTime now) {
    reconciliationAttempts = attempts;
    lastReconciledAt = now;
  }
}
