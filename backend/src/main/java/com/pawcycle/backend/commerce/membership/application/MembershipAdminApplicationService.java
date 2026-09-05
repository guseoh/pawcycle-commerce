package com.pawcycle.backend.commerce.membership.application;

import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.commerce.MembershipEvaluationService;
import com.pawcycle.backend.commerce.MembershipGradeRequest;
import com.pawcycle.backend.commerce.membership.api.MembershipGradeResponse;
import com.pawcycle.backend.commerce.membership.persistence.MembershipGradeView;
import com.pawcycle.backend.commerce.membership.persistence.MembershipPersistenceAdapter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipAdminApplicationService {
  private final MembershipPersistenceAdapter membership;
  private final AdminAuditService audits;
  private final MembershipEvaluationService membershipEvaluation;

  public MembershipAdminApplicationService(
      MembershipPersistenceAdapter membership,
      AdminAuditService audits,
      MembershipEvaluationService membershipEvaluation) {
    this.membership = membership;
    this.audits = audits;
    this.membershipEvaluation = membershipEvaluation;
  }

  @Transactional(readOnly = true)
  public List<MembershipGradeResponse> listGrades() {
    return membership.findGrades().stream()
        .map(MembershipAdminApplicationService::grade)
        .toList();
  }

  @Transactional
  public long createGrade(long adminId, MembershipGradeRequest request) {
    long gradeId = membership.createGrade(request);
    audits.append(adminId, "MEMBERSHIP_GRADE_CREATE", "MEMBERSHIP_GRADE", gradeId);
    return gradeId;
  }

  @Transactional
  public void evaluate(long adminId, long memberId) {
    membershipEvaluation.evaluate(memberId);
    audits.append(adminId, "MEMBERSHIP_EVALUATE", "MEMBER", memberId);
  }

  private static MembershipGradeResponse grade(MembershipGradeView view) {
    return new MembershipGradeResponse(
        view.gradeId(),
        view.code(),
        view.name(),
        view.minimumPurchaseAmount(),
        view.displayOrder(),
        view.active(),
        view.benefitCouponId());
  }
}
