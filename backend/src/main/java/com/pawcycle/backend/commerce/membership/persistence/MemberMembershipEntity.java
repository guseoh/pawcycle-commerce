package com.pawcycle.backend.commerce.membership.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA mapping for a commerce persistence record.
 */

@Entity
@Table(name = "member_memberships")
class MemberMembershipEntity {
  @Id
  @Column(name = "member_id")
  Long memberId;

  @Column(name = "grade_id", nullable = false)
  Long gradeId;

  @Column(name = "evaluated_purchase_amount", nullable = false, precision = 18, scale = 2)
  BigDecimal evaluatedPurchaseAmount;

  @Column(name = "evaluated_at", nullable = false)
  LocalDateTime evaluatedAt;
}
