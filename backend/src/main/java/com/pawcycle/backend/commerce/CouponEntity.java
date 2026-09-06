package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "coupons")
@Getter
public class CouponEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 100)
  String name;

  @Column(name = "discount_type", nullable = false, length = 20)
  String discountType;

  @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
  BigDecimal discountValue;

  @Column(name = "minimum_order_amount", nullable = false, precision = 12, scale = 2)
  BigDecimal minimumOrderAmount;

  @Column(name = "maximum_discount_amount", precision = 12, scale = 2)
  BigDecimal maximumDiscountAmount;

  @Column(nullable = false)
  boolean active;

  @Column(name = "valid_from", nullable = false)
  LocalDateTime validFrom;

  @Column(name = "valid_until", nullable = false)
  LocalDateTime validUntil;

  protected CouponEntity() {}

  public CouponEntity(
      String name,
      String discountType,
      BigDecimal discountValue,
      BigDecimal minimumOrderAmount,
      BigDecimal maximumDiscountAmount,
      LocalDateTime validFrom,
      LocalDateTime validUntil,
      boolean active) {
    this.name = name;
    this.discountType = discountType;
    this.discountValue = discountValue;
    this.minimumOrderAmount = minimumOrderAmount;
    this.maximumDiscountAmount = maximumDiscountAmount;
    this.validFrom = validFrom;
    this.validUntil = validUntil;
    this.active = active;
  }

  public void update(
      String name,
      String discountType,
      BigDecimal discountValue,
      BigDecimal minimumOrderAmount,
      BigDecimal maximumDiscountAmount,
      LocalDateTime validFrom,
      LocalDateTime validUntil,
      boolean active) {
    this.name = name;
    this.discountType = discountType;
    this.discountValue = discountValue;
    this.minimumOrderAmount = minimumOrderAmount;
    this.maximumDiscountAmount = maximumDiscountAmount;
    this.validFrom = validFrom;
    this.validUntil = validUntil;
    this.active = active;
  }

  public BigDecimal getMinimumOrderAmount() {
    return minimumOrderAmount;
  }

  public BigDecimal getDiscountValue() {
    return discountValue;
  }

  public BigDecimal getMaximumDiscountAmount() {
    return maximumDiscountAmount;
  }

  public String getDiscountType() {
    return discountType;
  }

  public boolean isActive() {
    return active;
  }

  public LocalDateTime getValidFrom() {
    return validFrom;
  }

  public LocalDateTime getValidUntil() {
    return validUntil;
  }
}
