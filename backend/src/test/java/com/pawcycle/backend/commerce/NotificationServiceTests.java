package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;

class NotificationServiceTests {
  @Test
  void mixedListLoadsReminderContextWithOneLeftJoinQuery() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    Map<String, Object> reminder = new LinkedHashMap<>();
    reminder.put("notificationId", 1L);
    reminder.put("type", "SUBSCRIPTION_DELIVERY_REMINDER");
    reminder.put("referenceType", "SCHEDULE");
    reminder.put("referenceId", 20L);
    reminder.put("readAt", null);
    reminder.put("createdAt", "created");
    reminder.put("subscriptionId", 10L);
    reminder.put("scheduledDate", Date.valueOf("2026-08-30"));
    Map<String, Object> ordinary = new LinkedHashMap<>();
    ordinary.put("notificationId", 2L);
    ordinary.put("type", "ORDER_PAID");
    ordinary.put("referenceType", "ORDER");
    ordinary.put("referenceId", 30L);
    ordinary.put("readAt", null);
    ordinary.put("createdAt", "created");
    ordinary.put("subscriptionId", null);
    ordinary.put("scheduledDate", null);
    when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(ordinary, reminder));

    List<CommerceRowResponse> result =
        new NotificationService(jdbc, java.time.Clock.systemUTC()).list(7L);

    assertThat(result).hasSize(2);
    assertThat(result.get(1).jsonValues())
        .containsEntry("subscriptionId", 10L)
        .containsEntry("scheduledDate", Date.valueOf("2026-08-30"));
    assertThat(result.get(0).jsonValues())
        .doesNotContainKey("subscriptionId")
        .doesNotContainKey("scheduledDate");
    verify(jdbc)
        .queryForList(
            org.mockito.ArgumentMatchers.contains("LEFT JOIN subscription_schedules"), eq(7L));
  }
}
