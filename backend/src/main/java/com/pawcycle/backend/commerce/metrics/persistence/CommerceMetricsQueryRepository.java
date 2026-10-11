package com.pawcycle.backend.commerce.metrics.persistence;

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
}
