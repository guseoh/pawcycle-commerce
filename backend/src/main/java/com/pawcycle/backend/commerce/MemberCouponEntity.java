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
@Table(name = "member_coupons")
class MemberCouponEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(name = "coupon_id", nullable = false)
  Long couponId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "reserved_order_id")
  Long reservedOrderId;

  @Column(name = "issued_at", nullable = false)
  LocalDateTime issuedAt;
}
