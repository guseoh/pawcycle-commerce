package com.pawcycle.backend.commerce.membership.persistence;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.hibernate.type.descriptor.java.JdbcTimestampJavaType;
import org.hibernate.type.internal.BasicTypeImpl;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipEvaluationPersistenceAdapter {
  private final EntityManager entities;
  private final Clock clock;

  public MembershipEvaluationPersistenceAdapter(EntityManager entities, Clock clock) {
    this.entities = entities;
    this.clock = clock;
  }

  public boolean memberExists(long memberId) {
    return entities.unwrap(Session.class)
        .createNativeQuery("SELECT COUNT(*) FROM members WHERE id=:member", Long.class)
        .setHibernateFlushMode(FlushMode.COMMIT).setParameter("member", memberId)
        .getSingleResult() == 1;
  }

  public BigDecimal paidAmountLastYear(long memberId) {
    return entities.unwrap(Session.class).createNativeQuery(
        "SELECT COALESCE(SUM(payment_amount),0) FROM orders WHERE member_id=:member AND status='PAID' AND paid_at>=:cutoff",
        BigDecimal.class).setHibernateFlushMode(FlushMode.COMMIT)
        .setParameter("member", memberId)
        .setParameter("cutoff", Timestamp.from(clock.instant().minus(365, ChronoUnit.DAYS)), timestampType())
        .getSingleResult();
  }

  public EvaluationGrade findGrade(BigDecimal amount) {
    return entities.unwrap(Session.class).createNativeQuery(
        "SELECT id AS gradeId,benefit_coupon_id AS benefitCouponId FROM membership_grades WHERE active=true AND minimum_purchase_amount<=:amount ORDER BY minimum_purchase_amount DESC,id DESC LIMIT 1",
        EvaluationGrade.class).addScalar("gradeId", Long.class).addScalar("benefitCouponId", Long.class)
        .setHibernateFlushMode(FlushMode.COMMIT).setParameter("amount", amount)
        .getResultList().stream().findFirst().orElse(null);
  }

  public CurrentMembership findMembershipForUpdate(long memberId) {
    // Preserve the PK lookup and InnoDB missing-row gap lock, rather than an entity lock.
    return entities.unwrap(Session.class).createNativeQuery(
        "SELECT grade_id AS gradeId FROM member_memberships WHERE member_id=:member FOR UPDATE",
        Long.class)
        .setHibernateFlushMode(FlushMode.COMMIT).setParameter("member", memberId)
        .getResultList().stream().map(CurrentMembership::new).findFirst().orElse(null);
  }

  public void saveMembership(long memberId, long gradeId, BigDecimal amount) {
    // Keep the second lock query in the original write protocol.
    if (findMembershipForUpdate(memberId) == null) {
      mutation("INSERT INTO member_memberships(member_id,grade_id,evaluated_purchase_amount,evaluated_at) VALUES (:member,:grade,:amount,:now)")
          .setParameter("member", memberId).setParameter("grade", gradeId)
          .setParameter("amount", amount).setParameter("now", now(), timestampType()).executeUpdate();
    } else {
      mutation("UPDATE member_memberships SET grade_id=:grade,evaluated_purchase_amount=:amount,evaluated_at=:now WHERE member_id=:member")
          .setParameter("grade", gradeId).setParameter("amount", amount)
          .setParameter("now", now(), timestampType()).setParameter("member", memberId).executeUpdate();
    }
  }

  public void recordChange(long memberId, Long previousGradeId, long gradeId, BigDecimal amount, Long benefitCouponId) {
    // Include the required amount omitted by MembershipHistoryEntity's legacy mapping.
    mutation("INSERT INTO membership_histories(member_id,from_grade_id,to_grade_id,evaluated_purchase_amount,changed_at) VALUES (:member,:previous,:grade,:amount,:now)")
        .setParameter("member", memberId).setParameter("previous", previousGradeId, Long.class)
        .setParameter("grade", gradeId).setParameter("amount", amount)
        .setParameter("now", now(), timestampType()).executeUpdate();
    if (benefitCouponId != null) {
      mutation("INSERT INTO member_coupons(member_id,coupon_id,status,issued_at) VALUES (:member,:coupon,'AVAILABLE',:now)")
          .setParameter("member", memberId).setParameter("coupon", benefitCouponId)
          .setParameter("now", now(), timestampType()).executeUpdate();
    }
  }

  private MutationQuery mutation(String sql) {
    // JDBC did not flush pending managed changes before these statements.
    return entities.unwrap(Session.class).createNativeMutationQuery(sql)
        .setHibernateFlushMode(FlushMode.COMMIT);
  }

  private static BasicTypeImpl<Timestamp> timestampType() {
    return new BasicTypeImpl<>(JdbcTimestampJavaType.INSTANCE, new MembershipTimestampJdbcType());
  }

  private Timestamp now() { return Timestamp.from(clock.instant()); }

  public record EvaluationGrade(long gradeId, Long benefitCouponId) {}
  public record CurrentMembership(long gradeId) {}
}
