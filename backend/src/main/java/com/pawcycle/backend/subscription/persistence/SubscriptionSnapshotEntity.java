package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Feature-local persistence mapping; state transitions remain in the existing use cases. */
@Entity(name = "SubCommandSnapshot")
@Table(name = "subscription_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionSnapshotEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subscription_id")
  private long subscriptionId;

  @Column(name = "source_plan_version_id")
  private long planVersionId;

  @Column(name = "package_total_krw")
  private long packagePriceKrw;

  @Column(name = "delivery_cycle_weeks")
  private int deliveryCycleWeeks;

  public SubscriptionSnapshotEntity(long subscriptionId, long planVersionId,
      int deliveryCycleWeeks, long packagePriceKrw) {
    this.subscriptionId = subscriptionId;
    this.planVersionId = planVersionId;
    this.deliveryCycleWeeks = deliveryCycleWeeks;
    this.packagePriceKrw = packagePriceKrw;
  }
}
