package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Shared stored response columns; database completion clock remains in the reservation adapter. */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SubscriptionReservationResponse {
  @Column(name = "payload_fingerprint", length = 64)
  private String fingerprint;

  @Column(name = "response_status")
  private Integer status;

  @Column(name = "response_body", columnDefinition = "json")
  private String bodyJson;

  @Column(name = "location_header", length = 512)
  private String location;

  @Column(name = "etag_header", length = 64)
  private String etag;

  protected SubscriptionReservationResponse(String fingerprint) {
    this.fingerprint = fingerprint;
  }
}
