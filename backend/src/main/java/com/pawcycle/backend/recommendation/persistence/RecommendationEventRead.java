package com.pawcycle.backend.recommendation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** Scalar mapping for recommendation aggregates; interaction commands retain their owner. */
@Entity
@Table(name = "interaction_events")
@Immutable
class RecommendationEventRead {
  @Id Long id;
  @Column(name = "member_id") Long memberId;
  @Column(name = "product_id") Long productId;
  @Column(name = "event_type") String eventType;
  @Column(name = "occurred_at") LocalDateTime occurredAt;
  protected RecommendationEventRead() {}
}
