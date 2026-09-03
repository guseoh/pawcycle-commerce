package com.pawcycle.backend.subscription;

import com.pawcycle.backend.commerce.NotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    prefix = "pawcycle.subscription.delivery-reminder",
    name = "enabled",
    havingValue = "true")
public class SubscriptionDeliveryReminderProcessor {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final NativeQueryExecutor jdbc;
  private final NotificationService notifications;
  private final Clock clock;
  private final int windowDays;

  public SubscriptionDeliveryReminderProcessor(
      NativeQueryExecutor jdbc,
      NotificationService notifications,
      Clock clock,
      @Value("${pawcycle.subscription.delivery-reminder.window-days:3}") int windowDays) {
    if (windowDays < 1) throw new IllegalArgumentException("window-days must be positive");
    this.jdbc = jdbc;
    this.notifications = notifications;
    this.clock = clock;
    this.windowDays = windowDays;
  }

  @Scheduled(fixedDelayString = "${pawcycle.subscription.delivery-reminder.fixed-delay-ms:60000}")
  @Transactional
  public int process() {
    LocalDate today = LocalDate.now(clock.withZone(SEOUL));
    jdbc.update(
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
        today.plusDays(windowDays));
    List<Map<String, Object>> eligible =
        jdbc.queryForList(
            "SELECT subscription.member_id,schedule.id schedule_id FROM subscription_schedules"
                + " schedule JOIN subscriptions subscription ON"
                + " subscription.id=schedule.subscription_id WHERE subscription.status='ACTIVE' AND"
                + " subscription.runtime_managed=true AND schedule.status='SCHEDULED' AND"
                + " schedule.scheduled_date>? AND schedule.scheduled_date<=? AND NOT EXISTS (SELECT"
                + " 1 FROM subscription_orders existing_order WHERE"
                + " existing_order.schedule_id=schedule.id)",
            today,
            today.plusDays(windowDays));
    for (Map<String, Object> row : eligible)
      notifications.create(
          ((Number) row.get("member_id")).longValue(),
          "SUBSCRIPTION_DELIVERY_REMINDER",
          "SCHEDULE",
          ((Number) row.get("schedule_id")).longValue());
    return eligible.size();
  }
}
