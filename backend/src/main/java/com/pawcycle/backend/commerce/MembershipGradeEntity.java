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
@Table(name = "membership_grades")
class MembershipGradeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, unique = true, length = 30)
  String code;

  @Column(nullable = false, length = 100)
  String name;

  @Column(name = "minimum_purchase_amount", nullable = false, precision = 18, scale = 2)
  BigDecimal minimumPurchaseAmount;

  @Column(name = "display_order", nullable = false)
  int displayOrder;

  @Column(nullable = false)
  boolean active;

  @Column(name = "benefit_coupon_id")
  Long benefitCouponId;
}
