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
@Entity(name = "SubCommandReservation")
@Table(name = "subscription_command_idempotency_results")
@IdClass(SubscriptionCommandReservationId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionCommandReservationEntity extends SubscriptionReservationResponse {
  @Id
  @Column(name = "member_id")
  private long memberId;

  @Id
  @Column(name = "subscription_id")
  private long subscriptionId;

  @Id
  @Column(name = "command_type", length = 30)
  private String command;

  @Id
  @Column(name = "idempotency_key", length = 128)
  private String key;

  public SubscriptionCommandReservationEntity(long memberId, long subscriptionId,
      String command, String key, String fingerprint) {
    super(fingerprint);
    this.memberId = memberId;
    this.subscriptionId = subscriptionId;
    this.command = command;
    this.key = key;
  }
}
