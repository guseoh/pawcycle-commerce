package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;
import org.hibernate.type.descriptor.jdbc.LocalDateTimeJdbcType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Feature-local persistence mapping; state transitions remain in the existing use cases. */
@Entity(name = "SubCreatedOrder")
@Table(name = "subscription_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionOrderEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id")
  private long memberId;

  @Column(name = "subscription_id")
  private long subscriptionId;

  @Column(name = "schedule_id")
  private long scheduleId;

  @Column(name = "effective_snapshot_id")
  private long snapshotId;

  @Column(name = "source_plan_version_id")
  private long planVersionId;

  @JdbcType(LocalDateJdbcType.class)
  @Column(name = "scheduled_date")
  private LocalDate scheduledDate;

  @JdbcType(LocalDateTimeJdbcType.class)
  @Column(name = "processed_at")
  private LocalDateTime processedAt;

  @Column(name = "package_total_krw", precision = 18, scale = 2)
  private BigDecimal total;

  private String status;

  public SubscriptionOrderEntity(long memberId, long subscriptionId, long scheduleId,
      long snapshotId, long planVersionId, LocalDate scheduledDate,
      LocalDateTime processedAt, BigDecimal total) {
    this.memberId = memberId;
    this.subscriptionId = subscriptionId;
    this.scheduleId = scheduleId;
    this.snapshotId = snapshotId;
    this.planVersionId = planVersionId;
    this.scheduledDate = scheduledDate;
    this.processedAt = processedAt;
    this.total = total;
    this.status = "CREATED";
  }
}
