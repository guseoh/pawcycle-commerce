package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionSchedulePersistence {
  private final NativeQueryExecutor jdbc;

  public SubscriptionSchedulePersistence(NativeQueryExecutor jdbc) {
    this.jdbc = jdbc;
  }

  public List<Long> heldScheduleIds(long subscriptionId) {
    return jdbc.queryForList(
        "SELECT id FROM subscription_schedules WHERE subscription_id=? AND status='HELD' ORDER BY"
            + " scheduled_date,id",
        Long.class,
        subscriptionId);
  }
}
