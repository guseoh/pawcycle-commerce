package com.pawcycle.backend.commerce.membership.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipPersistenceAdapter {
  private final EntityManager entities;

  public MembershipPersistenceAdapter(EntityManager entities) {
    this.entities = entities;
  }

  public long createGrade(CreateMembershipGradeCommand command) {
    var grade = new MembershipGradeEntity();
    grade.code = command.code();
    grade.name = command.name();
    grade.minimumPurchaseAmount = command.minimumPurchaseAmount();
    grade.displayOrder = command.displayOrder();
    grade.active = command.active();
    grade.benefitCouponId = command.benefitCouponId();
    // IDENTITY inserts immediately, before the caller appends the audit. Avoid flushing
    // unrelated pending entity changes, which the former JDBC INSERT did not flush.
    entities.persist(grade);
    return grade.id;
  }
}
