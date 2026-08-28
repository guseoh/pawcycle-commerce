package com.pawcycle.backend.subscription.v2;

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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class V2SubscriptionQueryApplicationServiceTests {
	@Test
	void nextDeliveryPreservesDecimalAddonAmounts() {
		V2SubscriptionJdbcStore store = mock(V2SubscriptionJdbcStore.class);
		V2SubscriptionQueryApplicationService service = new V2SubscriptionQueryApplicationService(
				store, new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));
		V2SubscriptionData.Subscription subscription = new V2SubscriptionData.Subscription(1L, 10L, "ACTIVE", 0L, null, 2, 5L);
		V2SubscriptionData.Snapshot snapshot = new V2SubscriptionData.Snapshot(5L, 2L, 20000L, 2, List.of());
		V2SubscriptionData.NextDeliverySchedule schedule = new V2SubscriptionData.NextDeliverySchedule(
				7L, LocalDate.of(2026, 8, 30), "SCHEDULED", null, null);
		when(store.findOwnedSubscription(10L, 1L)).thenReturn(subscription);
		when(store.findSnapshot(5L)).thenReturn(snapshot);
		when(store.findPendingChange(1L)).thenReturn(Optional.empty());
		when(store.findNextDeliverySchedule(1L)).thenReturn(Optional.of(schedule));
		when(store.findNextSchedule(eq(1L), any(LocalDate.class))).thenReturn(Optional.of(schedule.scheduledDate()));
		when(store.findScheduleViews(1L, 0, 20)).thenReturn(new V2SubscriptionData.Page<>(0, 20, 0L, List.of()));
		when(store.findCommandHistory(1L, 0, 20)).thenReturn(new V2SubscriptionData.Page<>(0, 20, 0L, List.of()));
		when(store.findSnapshotItemDetails(5L)).thenReturn(List.of());
		when(store.findScheduleAddons(7L)).thenReturn(List.of(new V2SubscriptionData.ScheduleAddon(
				7L, 2001L, 201L, "간식", "소형", 2, new BigDecimal("3000.55"))));

		Map<String, Object> body = service.detailBody(10L, 1L, 0, 20, 0, 20);

		@SuppressWarnings("unchecked")
		Map<String, Object> nextDelivery = (Map<String, Object>) body.get("nextDelivery");
		@SuppressWarnings("unchecked")
		Map<String, Object> addOn = (Map<String, Object>) ((List<?>) nextDelivery.get("addOns")).getFirst();
		assertThat(addOn.get("unitPriceKrw")).isEqualTo(new BigDecimal("3000.55"));
		assertThat(addOn.get("lineAmountKrw")).isEqualTo(new BigDecimal("6001.10"));
		assertThat(nextDelivery.get("addOnTotalKrw")).isEqualTo(new BigDecimal("6001.10"));
		assertThat(nextDelivery.get("orderTotalKrw")).isEqualTo(new BigDecimal("26001.10"));
	}
}
