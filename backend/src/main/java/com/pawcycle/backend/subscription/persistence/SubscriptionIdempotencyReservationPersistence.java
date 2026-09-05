package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.StoredIdempotencyResult;
import com.pawcycle.backend.subscription.SubscriptionOperationResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionIdempotencyReservationPersistence {
  private final JdbcTemplate jdbc;

  public SubscriptionIdempotencyReservationPersistence(JdbcTemplate jdbc) {
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

  public StoredIdempotencyResult lockCreationResult(long memberId, String key) {
    return jdbc.query(
            "SELECT payload_fingerprint,response_status,response_body,location_header,etag_header"
                + " FROM subscription_creation_idempotency_results WHERE member_id=? AND"
                + " idempotency_key=? FOR UPDATE",
            (rs, rowNum) ->
                new StoredIdempotencyResult(
                    rs.getString("payload_fingerprint"),
                    rs.getInt("response_status"),
                    rs.getString("response_body"),
                    rs.getString("location_header"),
                    rs.getString("etag_header")),
            memberId,
            key)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public void updateCreationResponse(
      long memberId,
      String key,
      long subscriptionId,
      SubscriptionOperationResult result,
      String bodyJson) {
    jdbc.update(
        "UPDATE subscription_creation_idempotency_results SET"
            + " subscription_id=?,response_status=?,response_body=?,location_header=?,etag_header=?,completed_at=COALESCE(completed_at,UTC_TIMESTAMP(6))"
            + " WHERE member_id=? AND idempotency_key=?",
        subscriptionId,
        result.status(),
        bodyJson,
        result.location(),
        result.etag(),
        memberId,
        key);
  }

  public void updateStoredCreationBody(long memberId, String key, String bodyJson) {
    jdbc.update(
        "UPDATE subscription_creation_idempotency_results SET response_body=? WHERE member_id=? AND"
            + " idempotency_key=?",
        bodyJson,
        memberId,
        key);
  }

  public StoredIdempotencyResult lockCommandResult(
      long memberId, long subscriptionId, String command, String key) {
    return jdbc.query(
            "SELECT payload_fingerprint,response_status,response_body,location_header,etag_header"
                + " FROM subscription_command_idempotency_results WHERE member_id=? AND"
                + " subscription_id=? AND command_type=? AND idempotency_key=? FOR UPDATE",
            (rs, rowNum) ->
                new StoredIdempotencyResult(
                    rs.getString("payload_fingerprint"),
                    rs.getInt("response_status"),
                    rs.getString("response_body"),
                    rs.getString("location_header"),
                    rs.getString("etag_header")),
            memberId,
            subscriptionId,
            command,
            key)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public void updateCommandResponse(
      long memberId,
      long subscriptionId,
      String command,
      String key,
      SubscriptionOperationResult result,
      String bodyJson) {
    jdbc.update(
        "UPDATE subscription_command_idempotency_results SET"
            + " response_status=?,response_body=?,location_header=?,etag_header=?,completed_at=COALESCE(completed_at,UTC_TIMESTAMP(6))"
            + " WHERE member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=?",
        result.status(),
        bodyJson,
        result.location(),
        result.etag(),
        memberId,
        subscriptionId,
        command,
        key);
  }

  public void updateStoredCommandBody(
      long memberId, long subscriptionId, String command, String key, String bodyJson) {
    jdbc.update(
        "UPDATE subscription_command_idempotency_results SET response_body=? WHERE member_id=? AND"
            + " subscription_id=? AND command_type=? AND idempotency_key=?",
        bodyJson,
        memberId,
        subscriptionId,
        command,
        key);
  }
}
