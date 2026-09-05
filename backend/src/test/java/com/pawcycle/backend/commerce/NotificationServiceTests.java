package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.commerce.notification.api.NotificationResponse;
import com.pawcycle.backend.commerce.notification.persistence.NotificationPersistenceAdapter;
import com.pawcycle.backend.commerce.notification.persistence.NotificationView;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationServiceTests {
  @Test
  void mixedListLoadsReminderContextWithOneLeftJoinQuery() {
    NotificationPersistenceAdapter adapter = mock(NotificationPersistenceAdapter.class);
    Timestamp createdAt = Timestamp.valueOf("2026-08-30 00:00:00");
    when(adapter.findByMemberId(7L))
        .thenReturn(
            List.of(
                new NotificationView(2L, "ORDER_PAID", "ORDER", 30L, null, createdAt, null, null),
                new NotificationView(1L, "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", 20L, null, createdAt, 10L, createdAt)));

    List<NotificationResponse> result = new NotificationService(adapter).list(7L);

    assertThat(result).hasSize(2);
    assertThat(result.get(1).subscriptionId()).isEqualTo(10L);
    assertThat(result.get(1).scheduledDate()).isEqualTo(createdAt);
    assertThat(result.get(0).subscriptionId()).isNull();
    assertThat(result.get(0).scheduledDate()).isNull();
    verify(adapter).findByMemberId(7L);
  }
}
