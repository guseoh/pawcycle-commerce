package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Feature-local persistence mapping; state transitions remain in the existing use cases. */
@Entity(name = "SubCreationReservation")
@Table(name = "subscription_creation_idempotency_results")
@IdClass(SubscriptionCreationReservationId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionCreationReservationEntity extends SubscriptionReservationResponse {
  @Id
  @Column(name = "member_id")
  private long memberId;

  @Id
  @Column(name = "idempotency_key", length = 128)
  private String key;

  @Column(name = "subscription_id")
  private Long subscriptionId;

  public SubscriptionCreationReservationEntity(long memberId, String key, String fingerprint) {
    super(fingerprint);
    this.memberId = memberId;
    this.key = key;
  }
}
