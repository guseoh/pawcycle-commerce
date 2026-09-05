package com.pawcycle.backend.subscription;

import com.pawcycle.backend.commerce.NotificationService;
import com.pawcycle.backend.subscription.persistence.SubscriptionDeliveryReminderPersistence;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
  private final SubscriptionDeliveryReminderPersistence persistence;
  private final NotificationService notifications;
  private final Clock clock;
  private final int windowDays;

  public SubscriptionDeliveryReminderProcessor(
      SubscriptionDeliveryReminderPersistence persistence,
      NotificationService notifications,
      Clock clock,
      @Value("${pawcycle.subscription.delivery-reminder.window-days:3}") int windowDays) {
    if (windowDays < 1) throw new IllegalArgumentException("window-days must be positive");
    this.persistence = persistence;
    this.notifications = notifications;
    this.clock = clock;
    this.windowDays = windowDays;
  }

  @Scheduled(fixedDelayString = "${pawcycle.subscription.delivery-reminder.fixed-delay-ms:60000}")
  @Transactional
  public int process() {
    LocalDate today = LocalDate.now(clock.withZone(SEOUL));
    persistence.deleteIneligible(today, today.plusDays(windowDays));
    var eligible = persistence.findEligible(today, today.plusDays(windowDays));
    for (var row : eligible)
      notifications.create(
          row.memberId(), "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", row.scheduleId());
    return eligible.size();
  }
}
