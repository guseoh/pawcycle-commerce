package com.pawcycle.backend.commerce.membership.application;

import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.commerce.CommerceRowResponse;
import com.pawcycle.backend.commerce.MembershipGradeRequest;
import com.pawcycle.backend.commerce.MembershipEvaluationService;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipAdminApplicationService {
  private final NativeQueryExecutor jdbc;
  private final AdminAuditService audits;
  private final MembershipEvaluationService membershipEvaluation;

  public MembershipAdminApplicationService(
      NativeQueryExecutor jdbc,
      AdminAuditService audits,
      MembershipEvaluationService membershipEvaluation) {
    this.jdbc = jdbc;
    this.audits = audits;
    this.membershipEvaluation = membershipEvaluation;
  }

  @Transactional(readOnly = true)
  public List<CommerceRowResponse> listGrades() {
    return CommerceRowResponse.from(
        jdbc.queryForList(
            "SELECT id AS gradeId,code,name,minimum_purchase_amount AS"
                + " minimumPurchaseAmount,display_order AS displayOrder,active,benefit_coupon_id AS"
                + " benefitCouponId FROM membership_grades ORDER BY display_order,id"));
  }

  @Transactional
  public long createGrade(long adminId, MembershipGradeRequest request) {
    jdbc.update(
        "INSERT INTO"
            + " membership_grades(code,name,minimum_purchase_amount,display_order,active,benefit_coupon_id)"
            + " VALUES (?,?,?,?,?,?)",
        request.code(),
        request.name(),
        request.minimumPurchaseAmount(),
        request.displayOrder(),
        request.active(),
        request.benefitCouponId());
    long gradeId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    audits.append(adminId, "MEMBERSHIP_GRADE_CREATE", "MEMBERSHIP_GRADE", gradeId);
    return gradeId;
  }

  @Transactional
  public void evaluate(long adminId, long memberId) {
    membershipEvaluation.evaluate(memberId);
    audits.append(adminId, "MEMBERSHIP_EVALUATE", "MEMBER", memberId);
  }
}
