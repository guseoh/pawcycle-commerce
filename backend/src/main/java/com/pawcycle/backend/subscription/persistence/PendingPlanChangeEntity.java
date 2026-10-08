package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Feature-local persistence mapping; state transitions remain in the existing use cases. */
@Entity(name = "SubCommandPending")
@Table(name = "pending_plan_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingPlanChangeEntity {
  @Id
  @Column(name = "subscription_id")
  private Long subscriptionId;

  @Column(name = "snapshot_id")
  private long snapshotId;

  @Column(name = "target_schedule_id")
  private long targetScheduleId;

  public PendingPlanChangeEntity(long subscriptionId, long snapshotId, long targetScheduleId) {
    this.subscriptionId = subscriptionId;
    this.snapshotId = snapshotId;
    this.targetScheduleId = targetScheduleId;
  }
}
