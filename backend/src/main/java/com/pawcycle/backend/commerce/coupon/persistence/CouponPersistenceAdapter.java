package com.pawcycle.backend.commerce.coupon.persistence;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.CouponRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class CouponPersistenceAdapter {
  private final JdbcTemplate queries;
  private final Clock clock;

  public CouponPersistenceAdapter(JdbcTemplate queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public long create(CouponRequest request) {
    queries.update(
        "INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,maximum_discount_amount,valid_from,valid_until,active) VALUES (?,?,?,?,?,?,?,?)",
        request.name(),
        request.discountType(),
        request.discountValue(),
        request.minimumOrderAmount(),
        request.maximumDiscountAmount(),
        Timestamp.valueOf(request.validFrom().withNano(0)),
        Timestamp.valueOf(request.validUntil().withNano(0)),
        request.active());
    return queries.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  public void update(long couponId, CouponRequest request) {
    requireCoupon(couponId);
    queries.update(
        "UPDATE coupons SET name=?,discount_type=?,discount_value=?,minimum_order_amount=?,maximum_discount_amount=?,valid_from=?,valid_until=?,active=? WHERE id=?",
        request.name(),
        request.discountType(),
        request.discountValue(),
        request.minimumOrderAmount(),
        request.maximumDiscountAmount(),
        Timestamp.valueOf(request.validFrom().withNano(0)),
        Timestamp.valueOf(request.validUntil().withNano(0)),
        request.active(),
        couponId);
  }

  public void issue(long couponId, long memberId) {
    requireCoupon(couponId);
    if (queries.queryForObject("SELECT COUNT(*) FROM members WHERE id=?", Integer.class, memberId)
        == 0) {
      throw new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    queries.update(
        "INSERT INTO member_coupons(member_id,coupon_id,status,issued_at) VALUES (?,?,'AVAILABLE',?)",
        memberId,
        couponId,
        Timestamp.from(clock.instant()));
  }

  public List<CouponView> findAll() {
    return queries.query(
        "SELECT id AS couponId,name,discount_type AS discountType,discount_value AS discountValue,minimum_order_amount AS minimumOrderAmount,maximum_discount_amount AS maximumDiscountAmount,valid_from AS validFrom,valid_until AS validUntil,active FROM coupons ORDER BY id",
        (rs, rowNumber) ->
            new CouponView(
                rs.getLong("couponId"),
                rs.getString("name"),
                rs.getString("discountType"),
                rs.getBigDecimal("discountValue"),
                rs.getBigDecimal("minimumOrderAmount"),
                rs.getBigDecimal("maximumDiscountAmount"),
                rs.getTimestamp("validFrom"),
                rs.getTimestamp("validUntil"),
                rs.getBoolean("active")));
  }

  public List<MemberCouponView> findForMember(long memberId) {
    return queries.query(
        "SELECT member_coupon.id AS memberCouponId,coupon.id AS couponId,coupon.name,coupon.discount_type AS discountType,coupon.discount_value AS discountValue,member_coupon.status,coupon.valid_from AS validFrom,coupon.valid_until AS validUntil FROM member_coupons member_coupon JOIN coupons coupon ON coupon.id=member_coupon.coupon_id WHERE member_coupon.member_id=? ORDER BY member_coupon.id DESC",
        (rs, rowNumber) ->
            new MemberCouponView(
                rs.getLong("memberCouponId"),
                rs.getLong("couponId"),
                rs.getString("name"),
                rs.getString("discountType"),
                rs.getBigDecimal("discountValue"),
                rs.getString("status"),
                rs.getTimestamp("validFrom"),
                rs.getTimestamp("validUntil")),
        memberId);
  }

  private void requireCoupon(long couponId) {
    if (queries.queryForObject("SELECT COUNT(*) FROM coupons WHERE id=?", Integer.class, couponId)
        == 0) {
      throw new CommerceException(404, "COUPON_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
  }
}
