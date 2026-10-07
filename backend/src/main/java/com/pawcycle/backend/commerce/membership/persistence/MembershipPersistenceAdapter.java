package com.pawcycle.backend.commerce.membership.persistence;

import com.pawcycle.backend.commerce.membership.api.MembershipGradeRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipPersistenceAdapter {
  private final JdbcTemplate queries;
  private final Clock clock;

  public MembershipPersistenceAdapter(JdbcTemplate queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
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
