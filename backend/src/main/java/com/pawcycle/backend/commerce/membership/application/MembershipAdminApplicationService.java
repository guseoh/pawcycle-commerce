package com.pawcycle.backend.commerce.membership.application;

import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.membership.application.MembershipEvaluationService;
import com.pawcycle.backend.commerce.membership.model.MembershipGradeView;
import com.pawcycle.backend.commerce.membership.persistence.CreateMembershipGradeCommand;
import com.pawcycle.backend.commerce.membership.persistence.MembershipPersistenceAdapter;
import com.pawcycle.backend.commerce.membership.persistence.MembershipQueryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipAdminApplicationService {
  private final MembershipPersistenceAdapter membership;
  private final MembershipQueryRepository queries;
  private final AdminAuditService audits;
  private final MembershipEvaluationService membershipEvaluation;

  public MembershipAdminApplicationService(
      MembershipPersistenceAdapter membership,
      MembershipQueryRepository queries,
      AdminAuditService audits,
      MembershipEvaluationService membershipEvaluation) {
    this.membership = membership;
    this.queries = queries;
    this.audits = audits;
    this.membershipEvaluation = membershipEvaluation;
  }

  @Transactional(readOnly = true)
  public List<MembershipGradeView> listGrades() {
    return List.copyOf(queries.findGrades());
  }

  @Transactional
  public long createGrade(long adminId, MembershipGradeCreateCommand request) {
    long gradeId = membership.createGrade(new CreateMembershipGradeCommand(
        request.code(), request.name(), request.minimumPurchaseAmount(),
        request.displayOrder(), request.active(), request.benefitCouponId()));
    audits.append(adminId, "MEMBERSHIP_GRADE_CREATE", "MEMBERSHIP_GRADE", gradeId);
    return gradeId;
  }

  @Transactional
  public void evaluate(long adminId, long memberId) {
    membershipEvaluation.evaluate(memberId);
    audits.append(adminId, "MEMBERSHIP_EVALUATE", "MEMBER", memberId);
  }

}
