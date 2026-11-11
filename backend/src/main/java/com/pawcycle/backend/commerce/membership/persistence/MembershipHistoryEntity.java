package com.pawcycle.backend.commerce.membership.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "membership_histories")
class MembershipHistoryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "member_id", nullable = false)
  Long memberId;

  @Column(name = "from_grade_id")
  Long fromGradeId;

  @Column(name = "to_grade_id", nullable = false)
  Long toGradeId;

  @Column(name = "changed_at", nullable = false)
  LocalDateTime changedAt;
}
