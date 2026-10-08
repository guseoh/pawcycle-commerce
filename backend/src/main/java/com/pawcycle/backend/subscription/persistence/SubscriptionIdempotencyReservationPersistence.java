package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.persistence.projection.StoredIdempotencyResult;
import com.pawcycle.backend.subscription.application.SubscriptionOperationResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.types.Projections;
import com.pawcycle.backend.member.domain.QMember;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionIdempotencyReservationPersistence {
  private final SubscriptionNativeSql nativeSql;
  private final EntityManager entities;
  private final JPAQueryFactory queries;
  private final QSubscriptionCreationReservationEntity creation = new QSubscriptionCreationReservationEntity("creation");
  private final QSubscriptionCommandReservationEntity commandRow = new QSubscriptionCommandReservationEntity("command");

  public SubscriptionIdempotencyReservationPersistence(EntityManager entities) {
    this.nativeSql = new SubscriptionNativeSql(entities);
    this.entities = entities;
    this.queries = new JPAQueryFactory(entities);
  }

  public boolean reserveCreation(long memberId, String key, String fingerprint) {
    var member = new QMember("member");
    requireParent(queries.select(member.id).from(member).where(member.id.eq(memberId))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne());
    if (!queries.select(creation.key).from(creation).where(creation.memberId.eq(memberId), creation.key.eq(key))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch().isEmpty()) return false;
    var row = new SubscriptionCreationReservationEntity(memberId, key, fingerprint);
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return true;
  }

  public boolean reserveCommand(
      long memberId, long subscriptionId, String command, String key, String fingerprint) {
    var parent = new QSubscriptionEntity("parent");
    requireParent(queries.select(parent.id).from(parent).where(parent.id.eq(subscriptionId), parent.memberId.eq(memberId))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne());
    if (!queries.select(commandRow.key).from(commandRow)
        .where(commandRow.memberId.eq(memberId), commandRow.subscriptionId.eq(subscriptionId), commandRow.command.eq(command), commandRow.key.eq(key))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch().isEmpty()) return false;
    var row = new SubscriptionCommandReservationEntity(memberId, subscriptionId, command, key, fingerprint);
    entities.persist(row);
    entities.flush();
    entities.detach(row);
    return true;
  }

  public StoredIdempotencyResult lockCreationResult(long memberId, String key) {
    return queries.select(Projections.constructor(StoredIdempotencyResult.class,
            creation.fingerprint, creation.status.coalesce(0), creation.bodyJson, creation.location, creation.etag))
        .from(creation).where(creation.memberId.eq(memberId), creation.key.eq(key))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch().stream().findFirst().orElseThrow();
  }

  public void updateCreationResponse(
      long memberId,
      String key,
      long subscriptionId,
      SubscriptionOperationResult result,
      String bodyJson) {
    nativeSql.update(
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
    queries.update(creation).set(creation.bodyJson, bodyJson)
        .where(creation.memberId.eq(memberId), creation.key.eq(key)).execute();
  }

  public StoredIdempotencyResult lockCommandResult(
      long memberId, long subscriptionId, String command, String key) {
    return queries.select(Projections.constructor(StoredIdempotencyResult.class,
            commandRow.fingerprint, commandRow.status.coalesce(0), commandRow.bodyJson, commandRow.location, commandRow.etag))
        .from(commandRow).where(commandRow.memberId.eq(memberId), commandRow.subscriptionId.eq(subscriptionId), commandRow.command.eq(command), commandRow.key.eq(key))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch().stream().findFirst().orElseThrow();
  }

  public void updateCommandResponse(
      long memberId,
      long subscriptionId,
      String command,
      String key,
      SubscriptionOperationResult result,
      String bodyJson) {
    nativeSql.update(
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
    queries.update(commandRow).set(commandRow.bodyJson, bodyJson)
        .where(commandRow.memberId.eq(memberId), commandRow.subscriptionId.eq(subscriptionId), commandRow.command.eq(command), commandRow.key.eq(key)).execute();
  }
  private void requireParent(Long id) {
    if (id == null) throw new org.springframework.dao.EmptyResultDataAccessException(1);
  }
}
