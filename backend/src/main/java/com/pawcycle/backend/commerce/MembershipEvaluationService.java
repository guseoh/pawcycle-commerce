package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.membership.persistence.MembershipEvaluationPersistenceAdapter;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns membership reevaluation and its grade-history/coupon side effects. */
@Service
public class MembershipEvaluationService {
  private final MembershipEvaluationPersistenceAdapter membership;

  public MembershipEvaluationService(MembershipEvaluationPersistenceAdapter membership) {
    this.membership = membership;
  }

  @Transactional
  public void evaluate(long memberId) {
    if (!membership.memberExists(memberId)) {
      throw new CommerceException(404, "MEMBER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    BigDecimal amount = membership.paidAmountLastYear(memberId);
    MembershipEvaluationPersistenceAdapter.EvaluationGrade grade = membership.findGrade(amount);
    if (grade == null) {
      throw new CommerceException(409, "MEMBERSHIP_GRADE_MISSING", "활성 등급이 없습니다.");
    }
    MembershipEvaluationPersistenceAdapter.CurrentMembership before =
        membership.findMembershipForUpdate(memberId);
    membership.saveMembership(memberId, grade.gradeId(), amount);
    if (before == null || before.gradeId() != grade.gradeId()) {
      membership.recordChange(
          memberId,
          before == null ? null : before.gradeId(),
          grade.gradeId(),
          amount,
          grade.benefitCouponId());
    }
  }
}
