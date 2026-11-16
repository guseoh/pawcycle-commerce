package com.pawcycle.backend.commerce.metrics.persistence;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class CommerceMetricsQueryRepository {
  private final EntityManager entities;

  public CommerceMetricsQueryRepository(EntityManager entities) {
    this.entities = entities;
  }

  public long countPendingOperations() {
    Long value =
        entities.createQuery(
            "select (select count(r) from OrderReturnEntity r where r.status='REQUESTED')"
                + " + (select count(r) from RefundEntity r where r.status in ('READY','FAILED','UNKNOWN'))"
                + " + (select count(p) from PaymentEntity p where p.status='UNKNOWN')",
            Long.class).getSingleResult();
    return value == null ? 0L : value;
  }

  /** Count and earliest timestamp of one backlog kind; {@code oldest} is null when the count is 0. */
  public record Backlog(long count, LocalDateTime oldest) {}

  /** NORMAL payments still PROCESSING whose checkout window closed at or before {@code cutoff}. */
  public Backlog normalProcessingExpiredBy(LocalDateTime cutoff) {
    return backlog(
        "select count(p), min(p.expiresAt) from PaymentEntity p where p.type = 'NORMAL'"
            + " and p.status = 'PROCESSING' and p.expiresAt is not null and p.expiresAt <= :cutoff",
        cutoff);
  }

  /** Payments of any type whose provider result is UNKNOWN, oldest by request time. */
  public Backlog unknownPayments() {
    return backlog(
        "select count(p), min(p.requestedAt) from PaymentEntity p where p.status = 'UNKNOWN'", null);
  }

  /** BILLING payments still PROCESSING, oldest by request time. */
  public Backlog billingProcessing() {
    return backlog(
        "select count(p), min(p.requestedAt) from PaymentEntity p where p.type = 'BILLING'"
            + " and p.status = 'PROCESSING'",
        null);
  }

  /** READY NORMAL payments the expiration job should already have closed (same rule as the job). */
  public Backlog readyExpiredBy(LocalDateTime cutoff) {
    return backlog(
        "select count(p), min(p.expiresAt) from PaymentEntity p where p.type = 'NORMAL'"
            + " and p.status = 'READY' and p.expiresAt is not null and p.expiresAt <= :cutoff",
        cutoff);
  }

  /** Refunds by unfinished status. Statuses without rows are absent from the map. */
  public Map<String, Long> unfinishedRefundsByStatus() {
    Map<String, Long> result = new HashMap<>();
    for (Object[] row :
        entities.createQuery(
                "select r.status, count(r) from RefundEntity r"
                    + " where r.status in ('READY','PROCESSING','FAILED','UNKNOWN') group by r.status",
                Object[].class)
            .getResultList()) {
      result.put((String) row[0], ((Number) row[1]).longValue());
    }
    return result;
  }

  private Backlog backlog(String jpql, LocalDateTime cutoff) {
    var query = entities.createQuery(jpql, Object[].class);
    if (cutoff != null) query.setParameter("cutoff", cutoff);
    Object[] row = query.getSingleResult();
    long count = row[0] == null ? 0L : ((Number) row[0]).longValue();
    return new Backlog(count, (LocalDateTime) row[1]);
  }
}
