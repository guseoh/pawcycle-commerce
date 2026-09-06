package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "orders")
public class CommerceOrderEntity {
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

  @Column(name = "original_amount", nullable = false, precision = 18, scale = 2)
  BigDecimal originalAmount;

  @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
  BigDecimal discountAmount;

  @Column(name = "shipping_fee", nullable = false, precision = 18, scale = 2)
  BigDecimal shippingFee;

  @Column(name = "recipient_name")
  String recipientName;

  @Column(name = "recipient_phone")
  String recipientPhone;

  @Column(name = "postal_code")
  String postalCode;

  @Column(name = "address_line1")
  String addressLine1;

  @Column(name = "address_line2")
  String addressLine2;

  @Column(name = "paid_at")
  LocalDateTime paidAt;

  protected CommerceOrderEntity() {}

  public CommerceOrderEntity(
      String orderNumber,
      long memberId,
      BigDecimal originalAmount,
      BigDecimal discountAmount,
      BigDecimal shippingFee,
      BigDecimal paymentAmount,
      String recipientName,
      String recipientPhone,
      String postalCode,
      String addressLine1,
      String addressLine2,
      LocalDateTime createdAt) {
    this.orderNumber = orderNumber;
    this.memberId = memberId;
    this.source = "ONE_TIME";
    this.status = "PAYMENT_PENDING";
    this.originalAmount = originalAmount;
    this.discountAmount = discountAmount;
    this.shippingFee = shippingFee;
    this.paymentAmount = paymentAmount;
    this.recipientName = recipientName;
    this.recipientPhone = recipientPhone;
    this.postalCode = postalCode;
    this.addressLine1 = addressLine1;
    this.addressLine2 = addressLine2;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public long getMemberId() {
    return memberId;
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public String getStatus() {
    return status;
  }

  public BigDecimal getOriginalAmount() {
    return originalAmount;
  }

  public BigDecimal getDiscountAmount() {
    return discountAmount;
  }

  public BigDecimal getShippingFee() {
    return shippingFee;
  }

  public BigDecimal getPaymentAmount() {
    return paymentAmount;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void markPaid(LocalDateTime now) {
    status = "PAID";
    paidAt = now;
  }

  public void markPaymentFailed() {
    status = "PAYMENT_FAILED";
  }

  public void markExpired() {
    status = "EXPIRED";
  }
}
