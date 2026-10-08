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
@Entity(name = "SubCommandSubscription")
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id")
  private long memberId;

  @Column(name = "sku_id")
  private long skuId;

  private int quantity;

  @Column(name = "delivery_cycle_weeks")
  private int deliveryCycleWeeks;

  @JdbcType(LocalDateJdbcType.class)
  @Column(name = "created_date")
  private LocalDate createdDate;

  @JdbcType(LocalDateJdbcType.class)
  @Column(name = "next_order_date")
  private LocalDate nextOrderDate;

  @Column(name = "pet_id")
  private Long petId;

  private String status;

  private long version;

  @Column(name = "current_snapshot_id")
  private Long currentSnapshotId;

  @Column(name = "legacy_api_visible")
  private boolean legacyApiVisible;

  @Column(name = "runtime_managed")
  private boolean runtimeManaged;

  public SubscriptionEntity(long memberId, long skuId, int cycleWeeks, long petId,
      LocalDate createdDate, LocalDate nextOrderDate) {
    this.memberId = memberId;
    this.skuId = skuId;
    this.quantity = 1;
    this.deliveryCycleWeeks = cycleWeeks;
    this.createdDate = createdDate;
    this.nextOrderDate = nextOrderDate;
    this.petId = petId;
    this.status = "ACTIVE";
    this.runtimeManaged = true;
  }
}
