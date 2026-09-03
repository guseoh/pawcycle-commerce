package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionIdempotencyReservationPersistence {
  private final NativeQueryExecutor jdbc;

  public SubscriptionIdempotencyReservationPersistence(NativeQueryExecutor jdbc) {
    this.jdbc = jdbc;
  }

  public boolean reserveCreation(long memberId, String key, String fingerprint) {
    jdbc.queryForObject("SELECT id FROM members WHERE id=? FOR UPDATE", Long.class, memberId);
    if (!jdbc.queryForList(
            "SELECT idempotency_key FROM subscription_creation_idempotency_results"
                + " WHERE member_id=? AND idempotency_key=? FOR UPDATE",
            String.class,
            memberId,
            key)
        .isEmpty()) return false;

    jdbc.update(
        "INSERT INTO"
            + " subscription_creation_idempotency_results(member_id,idempotency_key,payload_fingerprint)"
            + " VALUES (?,?,?)",
        memberId,
        key,
        fingerprint);
    return true;
  }

  public boolean reserveCommand(
      long memberId, long subscriptionId, String command, String key, String fingerprint) {
    jdbc.queryForObject(
        "SELECT id FROM subscriptions WHERE id=? AND member_id=? FOR UPDATE",
        Long.class,
        subscriptionId,
        memberId);
    if (!jdbc.queryForList(
            "SELECT idempotency_key FROM subscription_command_idempotency_results"
                + " WHERE member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=?"
                + " FOR UPDATE",
            String.class,
            memberId,
            subscriptionId,
            command,
            key)
        .isEmpty()) return false;

    jdbc.update(
        "INSERT INTO"
            + " subscription_command_idempotency_results(member_id,subscription_id,command_type,idempotency_key,payload_fingerprint)"
            + " VALUES (?,?,?,?,?)",
        memberId,
        subscriptionId,
        command,
        key,
        fingerprint);
    return true;
  }
}
