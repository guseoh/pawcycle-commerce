package com.pawcycle.backend.commerce.membership.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

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
