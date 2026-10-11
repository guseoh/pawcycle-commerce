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

  // DATETIME(6) is a UTC wall-clock value, read/bound through JDBC 4.2 without a Calendar.
  @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.LocalDateTimeJdbcType.class)
  @Column(name = "completed_at", insertable = false, updatable = false)
  private java.time.LocalDateTime completedAt;

  protected SubscriptionReservationResponse(String fingerprint) {
    this.fingerprint = fingerprint;
  }
}
