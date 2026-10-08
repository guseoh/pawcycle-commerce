package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import java.io.Serializable;

/** Composite reservation identities mirror the existing case-sensitive MySQL scope keys. */
final class SubscriptionReservationRows {
  private SubscriptionReservationRows() {}

  @MappedSuperclass
  static class Response {
    @Column(name = "payload_fingerprint", length = 64) String fingerprint;
    @Column(name = "response_status") Integer status;
    @Column(name = "response_body", columnDefinition = "json") String bodyJson;
    @Column(name = "location_header", length = 512) String location;
    @Column(name = "etag_header", length = 64) String etag;
  }

  @Entity(name = "SubCreationReservation") @Table(name = "subscription_creation_idempotency_results")
  @IdClass(CreationId.class)
  static class Creation extends Response {
    @Id @Column(name = "member_id") long memberId;
    @Id @Column(name = "idempotency_key", length = 128) String key;
    @Column(name = "subscription_id") Long subscriptionId;
    protected Creation() {}
  }
  record CreationId(long memberId, String key) implements Serializable {}

  @Entity(name = "SubCommandReservation") @Table(name = "subscription_command_idempotency_results")
  @IdClass(CommandId.class)
  static class Command extends Response {
    @Id @Column(name = "member_id") long memberId;
    @Id @Column(name = "subscription_id") long subscriptionId;
    @Id @Column(name = "command_type", length = 30) String command;
    @Id @Column(name = "idempotency_key", length = 128) String key;
    protected Command() {}
  }
  record CommandId(long memberId, long subscriptionId, String command, String key) implements Serializable {}
}
