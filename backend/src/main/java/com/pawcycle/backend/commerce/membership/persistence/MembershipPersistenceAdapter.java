package com.pawcycle.backend.commerce.membership.persistence;

import com.pawcycle.backend.commerce.MembershipGradeRequest;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipPersistenceAdapter {
  private final NativeQueryExecutor queries;
  private final Clock clock;

  public MembershipPersistenceAdapter(NativeQueryExecutor queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public List<MembershipGradeView> findGrades() {
    return queries.query(
        "SELECT id AS gradeId,code,name,minimum_purchase_amount AS minimumPurchaseAmount,display_order AS displayOrder,active,benefit_coupon_id AS benefitCouponId FROM membership_grades ORDER BY display_order,id",
        (rs, rowNumber) -> {
          long benefitCouponId = rs.getLong("benefitCouponId");
          boolean benefitCouponMissing = rs.wasNull();
          return new MembershipGradeView(
              rs.getLong("gradeId"),
              rs.getString("code"),
              rs.getString("name"),
              rs.getBigDecimal("minimumPurchaseAmount"),
              rs.getInt("displayOrder"),
              rs.getBoolean("active"),
              benefitCouponMissing ? null : benefitCouponId);
        });
  }

  public MembershipView findForMember(long memberId) {
    List<MembershipView> membership =
        queries.query(
            "SELECT grade.code,grade.name,membership.evaluated_purchase_amount AS evaluatedPurchaseAmount,membership.evaluated_at AS evaluatedAt FROM member_memberships membership JOIN membership_grades grade ON grade.id=membership.grade_id WHERE membership.member_id=?",
            (rs, rowNumber) ->
                new MembershipView(
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getBigDecimal("evaluatedPurchaseAmount"),
                    rs.getTimestamp("evaluatedAt")),
            memberId);
    if (!membership.isEmpty()) return membership.getFirst();
    return queries
        .query(
            "SELECT code,name,0 AS evaluatedPurchaseAmount,NULL AS evaluatedAt FROM membership_grades WHERE code='BASIC'",
            (rs, rowNumber) ->
                new MembershipView(
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getBigDecimal("evaluatedPurchaseAmount"),
                    rs.getTimestamp("evaluatedAt")))
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public long createGrade(MembershipGradeRequest request) {
    queries.update(
        "INSERT INTO membership_grades(code,name,minimum_purchase_amount,display_order,active,benefit_coupon_id) VALUES (?,?,?,?,?,?)",
        request.code(),
        request.name(),
        request.minimumPurchaseAmount(),
        request.displayOrder(),
        request.active(),
        request.benefitCouponId());
    return queries.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }
}
