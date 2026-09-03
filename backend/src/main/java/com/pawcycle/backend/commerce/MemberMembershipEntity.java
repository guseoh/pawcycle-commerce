package com.pawcycle.backend.commerce;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

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
