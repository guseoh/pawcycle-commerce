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
@Table(name = "coupons")
class CouponEntity {
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
}
