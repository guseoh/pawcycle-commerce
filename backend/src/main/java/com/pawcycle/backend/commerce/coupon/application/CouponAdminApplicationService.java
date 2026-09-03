package com.pawcycle.backend.commerce.coupon.application;

import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.CommerceRowResponse;
import com.pawcycle.backend.commerce.CouponRequest;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponAdminApplicationService {
  private final NativeQueryExecutor jdbc;
  private final AdminAuditService audits;
  private final Clock clock;

  public CouponAdminApplicationService(NativeQueryExecutor jdbc, AdminAuditService audits, Clock clock) {
    this.jdbc = jdbc;
    this.audits = audits;
    this.clock = clock;
  }

  @Transactional
  public long create(long adminId, CouponRequest request) {
    jdbc.update(
        "INSERT INTO"
            + " coupons(name,discount_type,discount_value,minimum_order_amount,maximum_discount_amount,valid_from,valid_until,active)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        request.name(),
        request.discountType(),
        request.discountValue(),
        request.minimumOrderAmount(),
        request.maximumDiscountAmount(),
        Timestamp.valueOf(request.validFrom().withNano(0)),
        Timestamp.valueOf(request.validUntil().withNano(0)),
        request.active());
    long couponId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    audits.append(adminId, "COUPON_CREATE", "COUPON", couponId);
    return couponId;
  }

  @Transactional
  public void update(long adminId, long couponId, CouponRequest request) {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE id=?", Integer.class, couponId) == 0) {
      throw new CommerceException(404, "COUPON_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    jdbc.update(
        "UPDATE coupons SET"
            + " name=?,discount_type=?,discount_value=?,minimum_order_amount=?,maximum_discount_amount=?,valid_from=?,valid_until=?,active=?"
            + " WHERE id=?",
        request.name(),
        request.discountType(),
        request.discountValue(),
        request.minimumOrderAmount(),
        request.maximumDiscountAmount(),
        Timestamp.valueOf(request.validFrom().withNano(0)),
        Timestamp.valueOf(request.validUntil().withNano(0)),
        request.active(),
        couponId);
    audits.append(adminId, "COUPON_UPDATE", "COUPON", couponId);
  }

  @Transactional
  public void issue(long adminId, long couponId, long memberId) {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE id=?", Integer.class, couponId) == 0) {
      throw new CommerceException(404, "COUPON_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    if (jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE id=?", Integer.class, memberId) == 0) {
      throw new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    jdbc.update(
        "INSERT INTO member_coupons(member_id,coupon_id,status,issued_at) VALUES"
            + " (?,?,'AVAILABLE',?)",
        memberId,
        couponId,
        Timestamp.from(clock.instant()));
    audits.append(adminId, "COUPON_ISSUE", "COUPON", couponId);
  }

  @Transactional(readOnly = true)
  public List<CommerceRowResponse> list() {
    return CommerceRowResponse.from(
        jdbc.queryForList(
            "SELECT id AS couponId,name,discount_type AS discountType,discount_value AS"
                + " discountValue,minimum_order_amount AS minimumOrderAmount,maximum_discount_amount AS"
                + " maximumDiscountAmount,valid_from AS validFrom,valid_until AS validUntil,active FROM"
                + " coupons ORDER BY id"));
  }
}
