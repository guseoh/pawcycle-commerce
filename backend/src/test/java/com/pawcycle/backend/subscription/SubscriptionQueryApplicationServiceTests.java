package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.persistence.SubscriptionAggregatePersistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import com.pawcycle.backend.subscription.api.NextDeliveryResponse;
import com.pawcycle.backend.subscription.api.SubscriptionAddonResponse;
import com.pawcycle.backend.subscription.api.SubscriptionDetailResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SubscriptionQueryApplicationServiceTests {
  @Test
  void nextDeliveryPreservesDecimalAddonAmounts() {
    SubscriptionAggregatePersistence store = mock(SubscriptionAggregatePersistence.class);
    SubscriptionQueryApplicationService service =
        new SubscriptionQueryApplicationService(
            store,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));
    SubscriptionProjection subscription =
        new SubscriptionProjection(1L, 10L, "ACTIVE", 0L, null, 2, 5L);
    SubscriptionSnapshot snapshot =
        new SubscriptionSnapshot(5L, 2L, 20000L, 2, List.of());
    NextDeliveryProjection schedule =
        new NextDeliveryProjection(
            7L, LocalDate.of(2026, 8, 30), "SCHEDULED", null, null);
    when(store.findOwnedSubscription(10L, 1L)).thenReturn(subscription);
    when(store.findSnapshot(5L)).thenReturn(snapshot);
    when(store.findPendingChange(1L)).thenReturn(Optional.empty());
    when(store.findNextDeliverySchedule(1L)).thenReturn(Optional.of(schedule));
    when(store.findNextSchedule(eq(1L), any(LocalDate.class)))
        .thenReturn(Optional.of(schedule.scheduledDate()));
    when(store.findScheduleViews(1L, 0, 20))
        .thenReturn(new PageProjection<>(0, 20, 0L, List.of()));
    when(store.findCommandHistory(1L, 0, 20))
        .thenReturn(new PageProjection<>(0, 20, 0L, List.of()));
    when(store.findSnapshotItemDetails(5L)).thenReturn(List.of());
    when(store.findScheduleAddons(7L))
        .thenReturn(
            List.of(
                new ScheduleAddonProjection(
                    7L, 2001L, 201L, "간식", "소형", 2, new BigDecimal("3000.55"))));
    when(store.scheduleAddonCount(7L)).thenReturn(1);

    SubscriptionDetailResponse body = service.detail(10L, 1L, 0, 20, 0, 20);

    NextDeliveryResponse nextDelivery = body.nextDelivery();
    SubscriptionAddonResponse addOn = nextDelivery.addOns().getFirst();
    assertThat(addOn.unitPriceKrw()).isEqualTo(new BigDecimal("3000.55"));
    assertThat(addOn.lineAmountKrw()).isEqualTo(new BigDecimal("6001.10"));
    assertThat(nextDelivery.addOnTotalKrw()).isEqualTo(new BigDecimal("6001.10"));
    assertThat(nextDelivery.orderTotalKrw()).isEqualTo(new BigDecimal("26001.10"));
    assertThat(body.availableActions())
        .containsExactly(
            "CHANGE_PLAN",
            "CHANGE_DELIVERY_CYCLE",
            "RESCHEDULE_NEXT",
            "SKIP_NEXT",
            "PAUSE",
            "SET_NEXT_DELIVERY_ADDON",
            "REMOVE_NEXT_DELIVERY_ADDON",
            "CANCEL",
            "UPDATE_SHIPPING_ADDRESS");
  }

  @Test
  void scheduledWithoutAddonExposesSetAddonButNotRemoveAddon() {
    SubscriptionAggregatePersistence store = mock(SubscriptionAggregatePersistence.class);
    SubscriptionQueryApplicationService service =
        new SubscriptionQueryApplicationService(
            store,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));
    SubscriptionProjection subscription =
        new SubscriptionProjection(1L, 10L, "ACTIVE", 0L, null, 2, 5L);
    SubscriptionSnapshot snapshot =
        new SubscriptionSnapshot(5L, 2L, 20000L, 2, List.of());
    NextDeliveryProjection schedule =
        new NextDeliveryProjection(
            7L, LocalDate.of(2026, 8, 30), "SCHEDULED", null, null);
    when(store.findOwnedSubscription(10L, 1L)).thenReturn(subscription);
    when(store.findSnapshot(5L)).thenReturn(snapshot);
    when(store.findPendingChange(1L)).thenReturn(Optional.empty());
    when(store.findNextDeliverySchedule(1L)).thenReturn(Optional.of(schedule));
    when(store.findNextSchedule(eq(1L), any(LocalDate.class)))
        .thenReturn(Optional.of(schedule.scheduledDate()));
    when(store.findScheduleViews(1L, 0, 20))
        .thenReturn(new PageProjection<>(0, 20, 0L, List.of()));
    when(store.findCommandHistory(1L, 0, 20))
        .thenReturn(new PageProjection<>(0, 20, 0L, List.of()));
    when(store.findSnapshotItemDetails(5L)).thenReturn(List.of());
    when(store.findScheduleAddons(7L)).thenReturn(List.of());

    SubscriptionDetailResponse body = service.detail(10L, 1L, 0, 20, 0, 20);

    assertThat(body.availableActions())
        .containsExactly(
            "CHANGE_PLAN",
            "CHANGE_DELIVERY_CYCLE",
            "RESCHEDULE_NEXT",
            "SKIP_NEXT",
            "PAUSE",
            "SET_NEXT_DELIVERY_ADDON",
            "CANCEL",
            "UPDATE_SHIPPING_ADDRESS");
  }

  @Test
  void recoverableStockHeldOnlyExposesRemoveAddonAndCancelWhenAddonExists() {
    SubscriptionAggregatePersistence store = mock(SubscriptionAggregatePersistence.class);
    SubscriptionQueryApplicationService service =
        new SubscriptionQueryApplicationService(
            store,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));
    SubscriptionProjection subscription =
        new SubscriptionProjection(1L, 10L, "ACTIVE", 1L, null, 2, 5L);
    SubscriptionSnapshot snapshot =
        new SubscriptionSnapshot(5L, 2L, 20000L, 2, List.of());
    NextDeliveryProjection schedule =
        new NextDeliveryProjection(
            7L, LocalDate.of(2026, 8, 28), "HELD", "ORDER_STOCK_UNAVAILABLE", null);
    when(store.findOwnedSubscription(10L, 1L)).thenReturn(subscription);
    when(store.findSnapshot(5L)).thenReturn(snapshot);
    when(store.findPendingChange(1L)).thenReturn(Optional.empty());
    when(store.findNextDeliverySchedule(1L)).thenReturn(Optional.of(schedule));
    when(store.findNextSchedule(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
    when(store.findScheduleViews(1L, 0, 20))
        .thenReturn(new PageProjection<>(0, 20, 0L, List.of()));
    when(store.findCommandHistory(1L, 0, 20))
        .thenReturn(new PageProjection<>(0, 20, 0L, List.of()));
    when(store.findSnapshotItemDetails(5L)).thenReturn(List.of());
    when(store.findScheduleAddons(7L))
        .thenReturn(
            List.of(
                new ScheduleAddonProjection(
                    7L, 2001L, 201L, "간식", "소형", 1, new BigDecimal("3000.00"))));
    when(store.scheduleAddonCount(7L)).thenReturn(1);

    SubscriptionDetailResponse body = service.detail(10L, 1L, 0, 20, 0, 20);

    assertThat(body.availableActions()).containsExactly("REMOVE_NEXT_DELIVERY_ADDON", "CANCEL");
    assertThat(body.issue().code()).isEqualTo("STOCK_UNAVAILABLE");
  }
}
