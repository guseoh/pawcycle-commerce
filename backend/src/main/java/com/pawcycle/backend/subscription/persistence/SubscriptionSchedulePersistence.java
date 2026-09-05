package com.pawcycle.backend.subscription.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionSchedulePersistence {
  private final JdbcTemplate jdbc;

  public SubscriptionSchedulePersistence(JdbcTemplate jdbc) {
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
