package com.pawcycle.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.commerce.NotificationService;
import com.pawcycle.backend.subscription.persistence.SubscriptionDeliveryReminderPersistence;
import com.pawcycle.backend.subscription.persistence.SubscriptionDeliveryReminderPersistence.ReminderTarget;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SubscriptionDeliveryReminderProcessorTests {
  @Test
  void staleReadOrUnreadRemindersAreCleanedBeforeReentryIsCreated() {
    SubscriptionDeliveryReminderPersistence jdbc =
        mock(SubscriptionDeliveryReminderPersistence.class);
    NotificationService notifications = mock(NotificationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC);
    LocalDate today = LocalDate.of(2026, 8, 28);
    when(jdbc.findEligible(today, today.plusDays(3)))
        .thenReturn(List.of(new ReminderTarget(10L, 20L)))
        .thenReturn(List.of(new ReminderTarget(10L, 20L)));
    SubscriptionDeliveryReminderProcessor processor =
        new SubscriptionDeliveryReminderProcessor(jdbc, notifications, clock, 3);

    assertThat(processor.process()).isEqualTo(1);
    assertThat(processor.process()).isEqualTo(1);

    InOrder order = inOrder(jdbc, notifications);
    for (int attempt = 0; attempt < 2; attempt++) {
      order.verify(jdbc).deleteIneligible(today, today.plusDays(3));
      order.verify(jdbc).findEligible(today, today.plusDays(3));
      order.verify(notifications).create(10L, "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", 20L);
    }
    verify(notifications, times(2)).create(10L, "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", 20L);
  }
}
