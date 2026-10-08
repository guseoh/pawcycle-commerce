package com.pawcycle.backend.commerce.order.persistence;

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
@Table(name = "subscription_order_context")
public class SubscriptionOrderContextEntity {
  @Id
  @Column(name = "order_id")
  Long orderId;

  @Column(name = "subscription_id", nullable = false)
  Long subscriptionId;

  @Column(name = "schedule_id", nullable = false, unique = true)
  Long scheduleId;

  @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.LocalDateJdbcType.class)
  @Column(name = "scheduled_date", nullable = false)
  java.time.LocalDate scheduledDate;
  @Column(name = "effective_snapshot_id") Long effectiveSnapshotId;
  @Column(name = "source_plan_version_id") Long sourcePlanVersionId;

  protected SubscriptionOrderContextEntity() {}

  public SubscriptionOrderContextEntity(long orderId, long subscriptionId, long scheduleId,
      long snapshotId, long planVersionId, java.time.LocalDate scheduledDate) {
    this.orderId = orderId;
    this.subscriptionId = subscriptionId;
    this.scheduleId = scheduleId;
    this.effectiveSnapshotId = snapshotId;
    this.sourcePlanVersionId = planVersionId;
    this.scheduledDate = scheduledDate;
  }
}
