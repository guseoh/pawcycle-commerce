package com.pawcycle.backend.subscription.persistence;

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

  private static String entity(boolean creation) {
    return creation ? "SubCreationReservation" : "SubCommandReservation";
  }
}
