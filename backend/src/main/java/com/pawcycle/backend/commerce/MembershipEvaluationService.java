package com.pawcycle.backend.commerce;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns membership reevaluation and its grade-history/coupon side effects. */
@Service
public class MembershipEvaluationService {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public MembershipEvaluationService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional
  public void evaluate(long memberId) {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE id=?", Integer.class, memberId)
        != 1) {
      throw new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    Timestamp now = now();
    BigDecimal amount =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(payment_amount),0) FROM orders WHERE member_id=? AND status='PAID'"
                + " AND paid_at>=?",
            BigDecimal.class,
            memberId,
            Timestamp.from(clock.instant().minus(365, ChronoUnit.DAYS)));
    Map<String, Object> grade =
        one(
            "SELECT id,benefit_coupon_id FROM membership_grades WHERE active=true AND"
                + " minimum_purchase_amount<=? ORDER BY minimum_purchase_amount DESC,id DESC LIMIT"
                + " 1",
            amount);
    if (grade == null) throw new CommerceException(409, "MEMBERSHIP_GRADE_MISSING", "활성 등급이 없습니다.");
    Map<String, Object> before =
        one("SELECT grade_id FROM member_memberships WHERE member_id=? FOR UPDATE", memberId);
    if (before == null)
      jdbc.update(
          "INSERT INTO"
              + " member_memberships(member_id,grade_id,evaluated_purchase_amount,evaluated_at)"
              + " VALUES (?,?,?,?)",
          memberId,
          number(grade, "id"),
          amount,
          now);
    else
      jdbc.update(
          "UPDATE member_memberships SET grade_id=?,evaluated_purchase_amount=?,evaluated_at=?"
              + " WHERE member_id=?",
          number(grade, "id"),
          amount,
          now,
          memberId);
    if (before == null || number(before, "grade_id") != number(grade, "id")) {
      jdbc.update(
          "INSERT INTO"
              + " membership_histories(member_id,from_grade_id,to_grade_id,evaluated_purchase_amount,changed_at)"
              + " VALUES (?,?,?,?,?)",
          memberId,
          before == null ? null : number(before, "grade_id"),
          number(grade, "id"),
          amount,
          now);
      if (grade.get("benefit_coupon_id") != null)
        jdbc.update(
            "INSERT INTO member_coupons(member_id,coupon_id,status,issued_at) VALUES"
                + " (?,?,'AVAILABLE',?)",
            memberId,
            number(grade, "benefit_coupon_id"),
            now);
    }
  }

  private Map<String, Object> one(String sql, Object... args) {
    var rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }
}
