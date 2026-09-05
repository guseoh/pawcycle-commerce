package com.pawcycle.backend.subscription.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionDeliveryReminderPersistence {
  private final JdbcTemplate jdbc;

  public SubscriptionDeliveryReminderPersistence(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ReminderTarget> findEligible(LocalDate today, LocalDate through) {
    return jdbc.query(
        "SELECT subscription.member_id,schedule.id schedule_id FROM subscription_schedules"
            + " schedule JOIN subscriptions subscription ON"
            + " subscription.id=schedule.subscription_id WHERE subscription.status='ACTIVE' AND"
            + " subscription.runtime_managed=true AND schedule.status='SCHEDULED' AND"
            + " schedule.scheduled_date>? AND schedule.scheduled_date<=? AND NOT EXISTS (SELECT"
            + " 1 FROM subscription_orders existing_order WHERE"
            + " existing_order.schedule_id=schedule.id)",
        (rs, row) -> new ReminderTarget(rs.getLong("member_id"), rs.getLong("schedule_id")),
        today,
        through);
  }

  public int deleteIneligible(LocalDate today, LocalDate through) {
    return jdbc.update(
        "DELETE FROM notifications WHERE type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " reference_type='SCHEDULE' AND NOT EXISTS (SELECT 1 FROM subscription_schedules"
            + " schedule JOIN subscriptions subscription ON"
            + " subscription.id=schedule.subscription_id WHERE"
            + " schedule.id=notifications.reference_id AND subscription.status='ACTIVE' AND"
            + " subscription.runtime_managed=true AND schedule.status='SCHEDULED' AND"
            + " schedule.scheduled_date>? AND schedule.scheduled_date<=? AND NOT EXISTS (SELECT 1"
            + " FROM subscription_orders existing_order WHERE"
            + " existing_order.schedule_id=schedule.id))",
        today,
        through);
  }

  public record ReminderTarget(long memberId, long scheduleId) {}
}
