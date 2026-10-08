package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Feature-local persistence mapping; state transitions remain in the existing use cases. */
@Entity(name = "SubCommandSchedule")
@Table(name = "subscription_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionScheduleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subscription_id")
  private long subscriptionId;

  @JdbcType(LocalDateJdbcType.class)
  @Column(name = "scheduled_date")
  private LocalDate scheduledDate;

  private String status;

  @Column(name = "hold_reason")
  private String holdReason;

  @Column(name = "effective_snapshot_id")
  private Long effectiveSnapshotId;

  public SubscriptionScheduleEntity(long subscriptionId, LocalDate scheduledDate) {
    this.subscriptionId = subscriptionId;
    this.scheduledDate = scheduledDate;
    this.status = "SCHEDULED";
  }
}
