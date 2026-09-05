package com.pawcycle.backend.commerce.membership.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipEvaluationPersistenceAdapter {
  private final JdbcTemplate queries;
  private final Clock clock;

  public MembershipEvaluationPersistenceAdapter(JdbcTemplate queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public boolean memberExists(long memberId) {
    return queries.queryForObject("SELECT COUNT(*) FROM members WHERE id=?", Integer.class, memberId) == 1;
  }

  public BigDecimal paidAmountLastYear(long memberId) {
    return queries.queryForObject(
        "SELECT COALESCE(SUM(payment_amount),0) FROM orders WHERE member_id=? AND status='PAID' AND paid_at>=?",
        BigDecimal.class,
        memberId,
        Timestamp.from(clock.instant().minus(365, ChronoUnit.DAYS)));
  }

  public EvaluationGrade findGrade(BigDecimal amount) {
    return queries
        .query(
            "SELECT id,benefit_coupon_id AS benefitCouponId FROM membership_grades WHERE active=true AND minimum_purchase_amount<=? ORDER BY minimum_purchase_amount DESC,id DESC LIMIT 1",
            (rs, rowNumber) -> {
              long benefitCouponId = rs.getLong("benefitCouponId");
              boolean missing = rs.wasNull();
              return new EvaluationGrade(rs.getLong("id"), missing ? null : benefitCouponId);
            },
            amount)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public CurrentMembership findMembershipForUpdate(long memberId) {
    return queries
        .query(
            "SELECT grade_id AS gradeId FROM member_memberships WHERE member_id=? FOR UPDATE",
            (rs, rowNumber) -> new CurrentMembership(rs.getLong("gradeId")),
            memberId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void saveMembership(long memberId, long gradeId, BigDecimal amount) {
    if (findMembershipForUpdate(memberId) == null) {
      queries.update(
          "INSERT INTO member_memberships(member_id,grade_id,evaluated_purchase_amount,evaluated_at) VALUES (?,?,?,?)",
          memberId,
          gradeId,
          amount,
          now());
    } else {
      queries.update(
          "UPDATE member_memberships SET grade_id=?,evaluated_purchase_amount=?,evaluated_at=? WHERE member_id=?",
          gradeId,
          amount,
          now(),
          memberId);
    }
  }

  public void recordChange(long memberId, Long previousGradeId, long gradeId, BigDecimal amount, Long benefitCouponId) {
    queries.update(
        "INSERT INTO membership_histories(member_id,from_grade_id,to_grade_id,evaluated_purchase_amount,changed_at) VALUES (?,?,?,?,?)",
        memberId,
        previousGradeId,
        gradeId,
        amount,
        now());
    if (benefitCouponId != null) {
      queries.update(
          "INSERT INTO member_coupons(member_id,coupon_id,status,issued_at) VALUES (?,?,'AVAILABLE',?)",
          memberId,
          benefitCouponId,
          now());
    }
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  public record EvaluationGrade(long gradeId, Long benefitCouponId) {}
  public record CurrentMembership(long gradeId) {}
}
