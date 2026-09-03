package com.pawcycle.backend.commerce.membership.application;

import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.commerce.CommerceRowResponse;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberBenefitApplicationService {
  private final NativeQueryExecutor jdbc;

  public MemberBenefitApplicationService(NativeQueryExecutor jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public List<CommerceRowResponse> coupons(long memberId) {
    return CommerceRowResponse.from(
        jdbc.queryForList(
            "SELECT member_coupon.id AS memberCouponId,coupon.id AS"
                + " couponId,coupon.name,coupon.discount_type AS discountType,coupon.discount_value AS"
                + " discountValue,member_coupon.status,coupon.valid_from AS"
                + " validFrom,coupon.valid_until AS validUntil FROM member_coupons member_coupon JOIN"
                + " coupons coupon ON coupon.id=member_coupon.coupon_id WHERE member_coupon.member_id=?"
                + " ORDER BY member_coupon.id DESC",
            memberId));
  }

  @Transactional(readOnly = true)
  public CommercePayload membership(long memberId) {
    Map<String, Object> membership =
        one(
            "SELECT grade.code,grade.name,membership.evaluated_purchase_amount AS"
                + " evaluatedPurchaseAmount,membership.evaluated_at AS evaluatedAt FROM"
                + " member_memberships membership JOIN membership_grades grade ON"
                + " grade.id=membership.grade_id WHERE membership.member_id=?",
            memberId);
    if (membership != null) return CommercePayload.from(membership);
    return CommercePayload.from(
        one(
            "SELECT code,name,0 AS evaluatedPurchaseAmount,NULL AS evaluatedAt FROM membership_grades"
                + " WHERE code='BASIC'"));
  }

  private Map<String, Object> one(String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : rows.getFirst();
  }
}
