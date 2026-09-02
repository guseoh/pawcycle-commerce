package com.pawcycle.backend.commerce;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the transaction for an admin mutation and its audit record. */
@Service
public class AdminCommerceService {
  private final CommerceService commerce;
  private final AdminAuditService audits;

  public AdminCommerceService(CommerceService commerce, AdminAuditService audits) {
    this.commerce = commerce;
    this.audits = audits;
  }

  @Transactional
  public void adjustInventory(long adminId, long skuId, int delta) {
    commerce.adjustInventory(skuId, delta);
    audits.append(adminId, "INVENTORY_ADJUST", "SKU", skuId);
  }

  @Transactional
  public long createCoupon(long adminId, CouponRequest request) {
    long couponId = commerce.createCoupon(request);
    audits.append(adminId, "COUPON_CREATE", "COUPON", couponId);
    return couponId;
  }

  @Transactional
  public void updateCoupon(long adminId, long couponId, CouponRequest request) {
    commerce.updateCoupon(couponId, request);
    audits.append(adminId, "COUPON_UPDATE", "COUPON", couponId);
  }

  @Transactional
  public void issueCoupon(long adminId, long couponId, long memberId) {
    commerce.issueCoupon(couponId, memberId);
    audits.append(adminId, "COUPON_ISSUE", "COUPON", couponId);
  }

  @Transactional
  public long createMembershipGrade(long adminId, MembershipGradeRequest request) {
    long gradeId = commerce.createMembershipGrade(request);
    audits.append(adminId, "MEMBERSHIP_GRADE_CREATE", "MEMBERSHIP_GRADE", gradeId);
    return gradeId;
  }

  @Transactional
  public void evaluateMembership(long adminId, long memberId) {
    commerce.evaluateMembership(memberId);
    audits.append(adminId, "MEMBERSHIP_EVALUATE", "MEMBER", memberId);
  }
}
