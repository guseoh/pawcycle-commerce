package com.pawcycle.backend.commerce.coupon.application;

import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.commerce.CouponRequest;
import com.pawcycle.backend.commerce.coupon.api.CouponResponse;
import com.pawcycle.backend.commerce.coupon.persistence.CouponPersistenceAdapter;
import com.pawcycle.backend.commerce.coupon.persistence.CouponView;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponAdminApplicationService {
  private final CouponPersistenceAdapter coupons;
  private final AdminAuditService audits;

  public CouponAdminApplicationService(CouponPersistenceAdapter coupons, AdminAuditService audits) {
    this.coupons = coupons;
    this.audits = audits;
  }

  @Transactional
  public long create(long adminId, CouponRequest request) {
    long couponId = coupons.create(request);
    audits.append(adminId, "COUPON_CREATE", "COUPON", couponId);
    return couponId;
  }

  @Transactional
  public void update(long adminId, long couponId, CouponRequest request) {
    coupons.update(couponId, request);
    audits.append(adminId, "COUPON_UPDATE", "COUPON", couponId);
  }

  @Transactional
  public void issue(long adminId, long couponId, long memberId) {
    coupons.issue(couponId, memberId);
    audits.append(adminId, "COUPON_ISSUE", "COUPON", couponId);
  }

  @Transactional(readOnly = true)
  public List<CouponResponse> list() {
    return coupons.findAll().stream().map(CouponAdminApplicationService::response).toList();
  }

  private static CouponResponse response(CouponView view) {
    return new CouponResponse(
        view.couponId(),
        view.name(),
        view.discountType(),
        view.discountValue(),
        view.minimumOrderAmount(),
        view.maximumDiscountAmount(),
        view.validFrom(),
        view.validUntil(),
        view.active());
  }
}
