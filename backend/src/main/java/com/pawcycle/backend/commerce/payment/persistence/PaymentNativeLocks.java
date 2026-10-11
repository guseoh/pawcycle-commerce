package com.pawcycle.backend.commerce.payment.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;

/** MySQL locking reads retain explicit predicates, JOIN targets and index/gap lock ordering. */
public final class PaymentNativeLocks {
  private final EntityManager entities;

  public PaymentNativeLocks(EntityManager entities) {
    this.entities = entities;
  }

  public <T> T first(String sql, Class<T> result, Object... arguments) {
    entities.flush();
    var query = entities.unwrap(Session.class).createNativeQuery(sql, result);
    for (int i = 0; i < arguments.length; i++) query.setParameter(i + 1, arguments[i]);
    return query.getResultList().stream().findFirst().orElse(null);
  }
}
