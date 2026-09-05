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
import java.time.LocalDateTime;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "member_coupons")
public class MemberCouponEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(name = "coupon_id", nullable = false)
  Long couponId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "coupon_id", insertable = false, updatable = false)
  CouponEntity coupon;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "reserved_order_id")
  Long reservedOrderId;

  @Column(name = "issued_at", nullable = false)
  LocalDateTime issuedAt;

  @Column(name = "used_at")
  LocalDateTime usedAt;

  protected MemberCouponEntity() {}

  public MemberCouponEntity(long memberId, long couponId, LocalDateTime issuedAt) {
    this.memberId = memberId;
    this.couponId = couponId;
    this.status = "AVAILABLE";
    this.issuedAt = issuedAt;
  }

  public Long getId() {
    return id;
  }

  public CouponEntity getCoupon() {
    return coupon;
  }

  public long getMemberId() {
    return memberId;
  }

  public long getCouponId() {
    return couponId;
  }

  public String getStatus() {
    return status;
  }
}
