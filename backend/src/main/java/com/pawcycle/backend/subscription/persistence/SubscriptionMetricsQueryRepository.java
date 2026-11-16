package com.pawcycle.backend.subscription.persistence;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionMetricsQueryRepository {
  private final EntityManager entities;

  public SubscriptionMetricsQueryRepository(EntityManager entities) {
    this.entities = entities;
  }

  public long countCompletedRows(boolean creation) {
    return entities.createQuery("select count(r) from " + entity(creation)
        + " r where r.completedAt is not null", Long.class).getSingleResult();
  }

  public long countCleanupCandidates(boolean creation, LocalDateTime cutoff) {
    return entities.createQuery("select count(r) from " + entity(creation)
        + " r where r.completedAt < :cutoff", Long.class)
        .setParameter("cutoff", cutoff).getSingleResult();
  }

  /**
   * SCHEDULED rounds of active, runtime-managed subscriptions whose date is before {@code today}
   * and that still have no subscription order: order automation should already have handled them.
   */
  public long countOverdueSchedules(LocalDate today) {
    return entities.createQuery(
            "select count(s) from SubCommandSchedule s, SubCommandSubscription sub"
                + " where sub.id = s.subscriptionId and sub.runtimeManaged = true"
                + " and sub.status = 'ACTIVE' and s.status = 'SCHEDULED' and s.scheduledDate < :today"
                + " and not exists (select 1 from SubCreatedOrder o where o.scheduleId = s.id)",
            Long.class)
        .setParameter("today", today)
        .getSingleResult();
  }

  /** HELD rounds of active, runtime-managed subscriptions by hold reason. */
  public Map<String, Long> countHeldSchedulesByReason() {
    Map<String, Long> result = new HashMap<>();
    for (Object[] row :
        entities.createQuery(
                "select s.holdReason, count(s) from SubCommandSchedule s, SubCommandSubscription sub"
                    + " where sub.id = s.subscriptionId and sub.runtimeManaged = true"
                    + " and sub.status = 'ACTIVE' and s.status = 'HELD' group by s.holdReason",
                Object[].class)
            .getResultList()) {
      result.put(row[0] == null ? "UNSPECIFIED" : (String) row[0], ((Number) row[1]).longValue());
    }
    return result;
  }

  private static String entity(boolean creation) {
    return creation ? "SubCreationReservation" : "SubCommandReservation";
  }
}
