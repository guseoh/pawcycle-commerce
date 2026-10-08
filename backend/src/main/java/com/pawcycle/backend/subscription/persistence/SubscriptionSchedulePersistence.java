package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionSchedulePersistence {
  private final EntityManager entities;

  public SubscriptionSchedulePersistence(EntityManager entities) {
    this.entities = entities;
  }

  public List<Long> heldScheduleIds(long subscriptionId) {
    return entities.createQuery("select s.id from SubCommandSchedule s where s.subscriptionId=:id and s.status='HELD' order by s.scheduledDate,s.id", Long.class)
        .setParameter("id", subscriptionId).getResultList();
  }
}
