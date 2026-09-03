package com.pawcycle.backend.subscription;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.subscription.api.SubscriptionCommandRequest;
import com.pawcycle.backend.subscription.persistence.SubscriptionIdempotencyReservationPersistence;
import com.pawcycle.backend.subscription.persistence.SubscriptionSchedulePersistence;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SubscriptionCommandStateTests {
  @Test
  void recoverableStockHeldRejectsUnapprovedScheduleMutationsBeforeTheyReachTheScheduleLock() {
    for (String command :
        List.of(
            "change-plan",
            "change-delivery-cycle",
            "reschedule-next",
            "skip-next",
            "pause",
            "set-next-delivery-addon")) {
      SubscriptionPersistenceAdapter store = mock(SubscriptionPersistenceAdapter.class);
      SubscriptionIdempotencyReservationPersistence reservations =
          mock(SubscriptionIdempotencyReservationPersistence.class);
      SubscriptionSchedulePersistence schedules = mock(SubscriptionSchedulePersistence.class);
      SubscriptionQueryApplicationService queries =
          mock(SubscriptionQueryApplicationService.class);
      SubscriptionProjection subscription =
          new SubscriptionProjection(1L, 10L, "ACTIVE", 0L, null, 2, 5L);
      NextDeliveryProjection held =
          new NextDeliveryProjection(
              7L, LocalDate.of(2026, 8, 28), "HELD", "ORDER_STOCK_UNAVAILABLE", null);
      when(store.lockOwnedSubscription(10L, 1L)).thenReturn(subscription);
      when(reservations.reserveCommand(eq(10L), eq(1L), anyString(), anyString(), anyString()))
          .thenReturn(true);
      when(store.findNextDeliverySchedule(1L)).thenReturn(Optional.of(held));
      SubscriptionCommandApplicationService service =
          new SubscriptionCommandApplicationService(
              store,
              reservations,
              schedules,
              queries,
              new ObjectMapper(),
              Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));

      assertThatThrownBy(
              () ->
                  service.command(
                      10L,
                      1L,
                      command,
                      "held-" + command,
                      "\"0\"",
                      SubscriptionCommandRequest.empty()))
          .isInstanceOf(SubscriptionApiException.class)
          .hasFieldOrPropertyWithValue("code", "SUBSCRIPTION_COMMAND_NOT_ALLOWED");
      verify(store, never()).lockNextScheduled(1L);
      verify(store, never()).incrementVersion(anyLong(), anyLong());
    }
  }
}
