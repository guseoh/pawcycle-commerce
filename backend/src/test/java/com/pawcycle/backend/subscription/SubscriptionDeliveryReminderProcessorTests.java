package com.pawcycle.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.commerce.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;

class SubscriptionDeliveryReminderProcessorTests {
  @Test
  void staleReadOrUnreadRemindersAreCleanedBeforeReentryIsCreated() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    NotificationService notifications = mock(NotificationService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC);
    LocalDate today = LocalDate.of(2026, 8, 28);
    when(jdbc.queryForList(anyString(), eq(today), eq(today.plusDays(3))))
        .thenReturn(List.of(Map.of("member_id", 10L, "schedule_id", 20L)))
        .thenReturn(List.of(Map.of("member_id", 10L, "schedule_id", 20L)));
    SubscriptionDeliveryReminderProcessor processor =
        new SubscriptionDeliveryReminderProcessor(jdbc, notifications, clock, 3);

    assertThat(processor.process()).isEqualTo(1);
    assertThat(processor.process()).isEqualTo(1);

    InOrder order = inOrder(jdbc, notifications);
    for (int attempt = 0; attempt < 2; attempt++) {
      order.verify(jdbc).update(anyString(), eq(today), eq(today.plusDays(3)));
      order.verify(jdbc).queryForList(anyString(), eq(today), eq(today.plusDays(3)));
      order.verify(notifications).create(10L, "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", 20L);
    }
    verify(notifications, times(2)).create(10L, "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", 20L);
  }
}
