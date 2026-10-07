package com.pawcycle.backend.commerce.membership.persistence;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipQueryRepository {
  private final EntityManager queries;

  public MembershipQueryRepository(EntityManager queries) {
    this.queries = queries;
  }

  public List<MembershipGradeView> findGrades() {
    return queries.createQuery("""
        select new com.pawcycle.backend.commerce.membership.persistence.MembershipGradeView(
            g.id, g.code, g.name, g.minimumPurchaseAmount, g.displayOrder, g.active, g.benefitCouponId)
        from MembershipGradeEntity g order by g.displayOrder, g.id
        """, MembershipGradeView.class).getResultList();
  }

  public MembershipView findForMember(long memberId) {
    List<MemberRow> membership = queries.createQuery("""
        select new com.pawcycle.backend.commerce.membership.persistence.MembershipQueryRepository$MemberRow(
            g.code, g.name, m.evaluatedPurchaseAmount, m.evaluatedAt)
        from MemberMembershipEntity m join MembershipGradeEntity g on g.id = m.gradeId
        where m.memberId = :memberId
        """, MemberRow.class).setParameter("memberId", memberId).getResultList();
    if (!membership.isEmpty()) return membership.getFirst().toView();
    return queries.createQuery("""
        select new com.pawcycle.backend.commerce.membership.persistence.MembershipQueryRepository$BasicGrade(
            g.code, g.name)
        from MembershipGradeEntity g where g.code = 'BASIC'
        """, BasicGrade.class).getResultList().stream().findFirst().orElseThrow().toView();
  }

  record MemberRow(String code, String name, BigDecimal evaluatedPurchaseAmount, LocalDateTime evaluatedAt) {
    MembershipView toView() {
      // Undo the existing Hibernate UTC JDBC calendar wrapping, as in the T04 Order read.
      Timestamp timestamp = evaluatedAt == null ? null : Timestamp.valueOf(LocalDateTime.ofInstant(
          Timestamp.valueOf(evaluatedAt).toInstant(), ZoneOffset.UTC));
      return new MembershipView(code, name, evaluatedPurchaseAmount, timestamp);
    }
  }

  record BasicGrade(String code, String name) {
    MembershipView toView() { return new MembershipView(code, name, BigDecimal.ZERO, null); }
  }
}
