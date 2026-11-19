package com.pawcycle.backend.commerce.membership.persistence;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.springframework.stereotype.Repository;

/**
 * Durable membership re-evaluation requests (PAYMENT-MEMBERSHIP-001), one row per member.
 *
 * <p>Statements touch only the request row by primary key, so recording a request inside a money
 * transaction takes no gap lock on {@code member_memberships} and cannot join the first-membership
 * gap-lock deadlock that used to roll back payment confirmation.
 */
@Repository
public class MembershipReevaluationRequestPersistenceAdapter {
  private final EntityManager entities;

  public MembershipReevaluationRequestPersistenceAdapter(EntityManager entities) {
    this.entities = entities;
  }

  /**
   * Inserts a due request, or marks an existing one as new: a higher version (so an evaluation that
   * is already running does not delete it), due now, attempts reset.
   */
  public void upsert(long memberId, Instant now) {
    mutation("INSERT INTO membership_reevaluation_requests"
            + "(member_id,request_version,requested_at,attempts,next_attempt_at) "
            + "VALUES (:member,1,:now,0,:now) AS incoming "
            + "ON DUPLICATE KEY UPDATE request_version=membership_reevaluation_requests.request_version+1,"
            + "requested_at=incoming.requested_at,attempts=0,next_attempt_at=incoming.next_attempt_at")
        .setParameter("member", memberId)
        .setParameter("now", Timestamp.from(now))
        .executeUpdate();
  }

  /** Oldest due requests first; read without locks, each one is then handled in its own transaction. */
  @SuppressWarnings("unchecked")
  public List<DueRequest> findDue(Instant now, int limit) {
    List<Object[]> rows = entities.unwrap(Session.class)
        .createNativeQuery("SELECT member_id,request_version,attempts FROM membership_reevaluation_requests "
            + "WHERE next_attempt_at<=:now ORDER BY next_attempt_at,member_id LIMIT :limit", Object[].class)
        .setHibernateFlushMode(FlushMode.COMMIT)
        .setParameter("now", Timestamp.from(now))
        .setParameter("limit", limit)
        .getResultList();
    return rows.stream()
        .map(row -> new DueRequest(
            ((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).intValue()))
        .toList();
  }

  /** Removes the request only if no newer request arrived; returns whether it was removed. */
  public boolean deleteIfUnchanged(long memberId, long version) {
    return mutation("DELETE FROM membership_reevaluation_requests WHERE member_id=:member AND request_version=:version")
        .setParameter("member", memberId)
        .setParameter("version", version)
        .executeUpdate() == 1;
  }

  /** Pushes a failed request back, unless a newer request arrived meanwhile (that one stays due). */
  public void postponeIfUnchanged(long memberId, long version, Instant nextAttemptAt) {
    mutation("UPDATE membership_reevaluation_requests SET attempts=attempts+1,next_attempt_at=:next "
            + "WHERE member_id=:member AND request_version=:version")
        .setParameter("next", Timestamp.from(nextAttemptAt))
        .setParameter("member", memberId)
        .setParameter("version", version)
        .executeUpdate();
  }

  private MutationQuery mutation(String sql) {
    return entities.unwrap(Session.class).createNativeMutationQuery(sql).setHibernateFlushMode(FlushMode.COMMIT);
  }

  public record DueRequest(long memberId, long version, int attempts) {}
}
