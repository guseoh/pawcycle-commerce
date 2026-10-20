package com.pawcycle.backend.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pawcycle.backend.subscription.api.*;
import com.pawcycle.backend.subscription.domain.SubscriptionFailure;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import com.pawcycle.backend.subscription.persistence.SubscriptionAggregatePersistence;
import com.pawcycle.backend.subscription.persistence.projection.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

class SubscriptionReadBoundaryTests {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final LocalDate DATE = LocalDate.of(2026, 8, 29);
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-28T15:00:00Z"), ZoneOffset.UTC);

  static Stream<Arguments> detailCases() {
    return Stream.of("2026-08-28T14:59:59.999999Z", "2026-08-28T15:00:00Z").flatMap(time ->
        Stream.of("ACTIVE", "PAUSED", "CANCELED").flatMap(status ->
            Stream.of("SCHEDULED", "MISSING_SHIPPING_ADDRESS", "MISSING_BILLING_METHOD", "PAYMENT_RETRY_EXHAUSTED",
                "PAYMENT_RETRY_STOCK_UNAVAILABLE", "ORDER_STOCK_UNAVAILABLE").flatMap(reason ->
                Stream.of(null, 10L, 11L, 12L).map(snapshot -> Arguments.of(time, status, reason, snapshot)))));
  }

  @ParameterizedTest @MethodSource("detailCases")
  void frozenQueryJsonAndEveryPersistenceCallRemainIdentical(String time, String status, String reason, Long effective) throws Exception {
    var store = detailStore(status, reason, effective);
    var clock = Clock.fixed(Instant.parse(time), ZoneOffset.UTC);
    var frozen = new FrozenSubscriptionQueryReference(store, JSON, clock);
    var actual = new SubscriptionQueryApplicationService(store, JSON, clock);
    var expected = frozen.subscription(7, 1, 1, 2, 2, 3);
    var calls = calls(store);
    clearInvocations(store);
    var view = actual.subscription(7, 1, 1, 2, 2, 3);
    assertThat(calls(store)).containsExactlyElementsOf(calls);
    assertThat(JSON.writeValueAsString(SubscriptionDetailResponse.from(view))).isEqualTo(JSON.writeValueAsString(expected.body()));
    assertThat(SubscriptionDetailResponse.from(view)).isEqualTo(expected.body());
    // The existing neutral model must preserve the durable response schema before it can replace the DTO.
    String legacyJson = JSON.writeValueAsString(expected.body());
    assertThat(JSON.writeValueAsString(view)).isEqualTo(legacyJson);
    var restored = JSON.readValue(legacyJson, SubscriptionReadModel.Detail.class);
    assertThat(JSON.writeValueAsString(restored)).isEqualTo(legacyJson);
    assertThat(JSON.writeValueAsString(SubscriptionDetailResponse.from(restored))).isEqualTo(legacyJson);
    verify(store).findNextSchedule(1, LocalDate.of(2026, 8, time.contains("14:59") ? 28 : 29));
  }

  @Test
  void frozenBatchedListKeepsNullablePetOrderPagingAndNoAdditionalPersistenceCalls() throws Exception {
    var store = mock(SubscriptionAggregatePersistence.class);
    var a = new SubscriptionProjection(1, 7, "ACTIVE", 9, null, 2, 10L);
    var b = new SubscriptionProjection(2, 7, "PAUSED", 3, 8L, 4, 11L);
    when(store.findSubscriptions(7, 1, 2)).thenReturn(new PageProjection<>(1, 2, 5, List.of(b, a)));
    when(store.findOwnedPets(eq(7L), anyList())).thenReturn(Map.of(8L, new PetProjection(8, "pet", "DOG", null, null)));
    when(store.findSnapshots(anyList())).thenReturn(Map.of(10L, new SubscriptionSnapshotBase(10, 100, 20000, 2), 11L, new SubscriptionSnapshotBase(11, 101, 30000, 4)));
    when(store.findSnapshotItems(anyList())).thenReturn(Map.of(10L, List.of(new SubscriptionItemProjection(99, 3)), 11L, List.of()));
    when(store.findNextSchedules(anyList(), any())).thenReturn(Map.of(1L, DATE));
    var frozen = new FrozenSubscriptionQueryReference(store, JSON, CLOCK);
    var expected = frozen.subscriptions(7, 1, 2);
    var calls = calls(store); clearInvocations(store);
    var actual = new SubscriptionQueryApplicationService(store, JSON, CLOCK).subscriptions(7, 1, 2);
    assertThat(calls(store)).containsExactlyElementsOf(calls);
    assertThat(JSON.writeValueAsString(PageResponse.from(actual, SubscriptionSummaryResponse::from))).isEqualTo(JSON.writeValueAsString(expected));
    assertThat(actual.items()).extracting(SubscriptionReadModel.Summary::subscriptionId).containsExactly(2L, 1L);
    assertThat(actual.items().getLast().pet()).isNull();
    assertThat(actual.items().getFirst().nextScheduledDate()).isNull();
  }

  @Test
  void pageValidationAndUnknownIssueKeepOriginalFailureBeforeLaterReads() {
    var store = detailStore("ACTIVE", "UNKNOWN", null);
    var actual = new SubscriptionQueryApplicationService(store, JSON, CLOCK);
    var frozen = new FrozenSubscriptionQueryReference(store, JSON, CLOCK);
    assertThatThrownBy(() -> actual.subscription(7, 1, -1, 20, 0, 20))
        .isInstanceOf(SubscriptionFailure.class).hasMessage("page 값을 확인해 주세요.");
    verifyNoInteractions(store);
    assertThatThrownBy(() -> frozen.subscription(7, 1, 0, 101, 0, 20)).isInstanceOf(SubscriptionFailure.class);
    verifyNoInteractions(store);
    assertThatThrownBy(() -> actual.detail(7, 1, 1, 2, 2, 3)).isInstanceOf(IllegalStateException.class).hasMessage("알 수 없는 Subscription issue입니다.");
    var calls = calls(store);clearInvocations(store);
    assertThatThrownBy(() -> frozen.detail(7, 1, 1, 2, 2, 3)).isInstanceOf(IllegalStateException.class).hasMessage("알 수 없는 Subscription issue입니다.");
    assertThat(calls(store)).containsExactlyElementsOf(calls);
  }

  @Test
  void dtoFactoriesKeepNullEmptyListsAndPublicMutableListConstructors() throws Exception {
    var nulls = new SubscriptionReadModel.Detail(1, null, 2, null, null, null, null, null, null, null, null, null, null);
    var expected = new SubscriptionDetailResponse(1, null, 2, null, null, null, null, null, null, null, null, null, null);
    assertThat(JSON.writeValueAsString(SubscriptionDetailResponse.from(nulls))).isEqualTo(JSON.writeValueAsString(expected));
    assertThat(SubscriptionSnapshotResponse.from(new SubscriptionReadModel.Snapshot(1, 2, 3, null)).items()).isNull();
    assertThat(NextDeliveryResponse.from(new SubscriptionReadModel.NextDelivery(1, null, null, 2, 3, 4, null, null, null, null)).items()).isNull();
    assertThat(PendingChangeResponse.from(new SubscriptionReadModel.PendingChange(1, null, 2, 3, 4, null)).items()).isNull();
    assertThat(PageResponse.from(new SubscriptionReadModel.Page<SubscriptionReadModel.Schedule>(1, 2, 3, null), ScheduleResponse::from).items()).isNull();
    var nullableItems = Arrays.asList(new SubscriptionReadModel.Item(7, 2), null, new SubscriptionReadModel.Item(7, 2));
    assertThat(SubscriptionSnapshotResponse.from(new SubscriptionReadModel.Snapshot(1, 2, 3, nullableItems)).items())
        .containsExactly(new SubscriptionItemResponse(7, 2), null, new SubscriptionItemResponse(7, 2));
    var actions = new ArrayList<>(List.of("CANCEL", "CANCEL"));
    var page = new ArrayList<ScheduleResponse>();
    var dto = new PageResponse<>(0, 20, 0, page); page.add(null);
    assertThat(dto.items()).isSameAs(page).containsExactly((ScheduleResponse) null);
    var view = new SubscriptionReadModel.Detail(1, "ACTIVE", 2, null, null, null, null, null, null, null,
        actions, new SubscriptionReadModel.Page<>(0, 20, 0, List.of()), new SubscriptionReadModel.Page<>(0, 20, 0, List.of()));
    assertThat(SubscriptionDetailResponse.from(view).availableActions()).isSameAs(actions).containsExactly("CANCEL", "CANCEL");
    assertThat(SubscriptionDetailResponse.from(view).schedules().items()).isEmpty();
  }

  @Test
  void supportKeepsLegacyDtoJsonReplayAndMalformedBodyFailure() throws Exception {
    var store = detailStore("ACTIVE", "SCHEDULED", 11L);
    var frozen = new FrozenSubscriptionQueryReference(store, JSON, CLOCK).detail(7, 1, 1, 2, 2, 3);
    var view = new SubscriptionQueryApplicationService(store, JSON, CLOCK).detail(7, 1, 1, 2, 2, 3);
    var support = new SubscriptionApplicationSupport(JSON, CLOCK);
    String legacyJson = JSON.writeValueAsString(frozen);
    assertThat(SubscriptionDetailResponse.from(view)).isEqualTo(frozen);
    assertThat(support.bodyJson(view)).isEqualTo(legacyJson);
    assertThat(support.responseBody(legacyJson)).isExactlyInstanceOf(SubscriptionReadModel.Detail.class);
    assertThat(support.bodyJson(support.responseBody(legacyJson))).isEqualTo(legacyJson);
    assertThat(legacyJson).contains("3703.8800", "20000", "2026-08-28T23:59:59.123456+09:00");
    assertThatThrownBy(() -> support.responseBody("{invalid"))
        .isInstanceOf(IllegalStateException.class).hasMessage("저장된 멱등 결과를 읽을 수 없습니다.").hasCauseInstanceOf(Exception.class);
  }

  private static List<String> calls(SubscriptionAggregatePersistence store) {
    return mockingDetails(store).getInvocations().stream()
        .map(call -> call.getMethod().getName() + Arrays.deepToString(call.getArguments())).toList();
  }

  @Test
  void pinnedPreRefactorStoredJsonKeepsNullsPagesAndHistoryWhenDecodedAndMapped() {
    String legacy = "{\"subscriptionId\":1,\"status\":\"PAUSED\",\"version\":17,\"pet\":null,\"currentSnapshot\":null,\"nextScheduledDate\":null,\"pendingSnapshot\":null,\"nextDelivery\":null,\"pendingChange\":null,\"issue\":null,\"availableActions\":[\"RESUME\",\"CANCEL\"],\"schedules\":{\"page\":2,\"size\":3,\"totalElements\":9,\"items\":[{\"scheduleId\":7,\"scheduledDate\":null,\"status\":\"SKIPPED\",\"effectiveSnapshotId\":null},null]},\"commandHistory\":{\"page\":1,\"size\":2,\"totalElements\":4,\"items\":[{\"commandType\":\"PAUSE\",\"result\":\"SUCCEEDED\",\"occurredAt\":\"2026-10-20T10:00:00.123456+09:00\"},null]}}";
    var support = new SubscriptionApplicationSupport(JSON, CLOCK);
    var restored = support.responseBody(legacy);
    assertThat(support.bodyJson(restored)).isEqualTo(legacy);
    assertThat(JSON.writeValueAsString(SubscriptionDetailResponse.from(restored))).isEqualTo(legacy);
    assertThat(JSON.writeValueAsString(JSON.readValue(legacy, SubscriptionDetailResponse.class))).isEqualTo(legacy);
    assertThat(support.responseBody("null")).isNull();
  }

  private static SubscriptionAggregatePersistence detailStore(String status, String reason, Long effective) {
    var store = mock(SubscriptionAggregatePersistence.class);
    when(store.findOwnedSubscription(7, 1)).thenReturn(new SubscriptionProjection(1, 7, status, 17, 8L, 2, 10L));
    when(store.findOwnedPet(7, 8)).thenReturn(new PetProjection(8, "pet", "DOG", "breed", new BigDecimal("3.2000")));
    when(store.findSnapshot(10)).thenReturn(new SubscriptionSnapshot(10, 100, 20000, 2, List.of(new SubscriptionItemProjection(99, 3))));
    when(store.findSnapshot(11)).thenReturn(new SubscriptionSnapshot(11, 101, 30000, 4, List.of(new SubscriptionItemProjection(88, 2))));
    when(store.findSnapshot(12)).thenReturn(new SubscriptionSnapshot(12, 102, 40000, 8, List.of(new SubscriptionItemProjection(77, 1))));
    when(store.findPendingChange(1)).thenReturn(Optional.of(new PendingSubscriptionChange(11, 21, DATE)));
    when(store.findNextDeliverySchedule(1)).thenReturn(Optional.of(new NextDeliveryProjection(21, DATE, reason.equals("SCHEDULED") ? "SCHEDULED" : "HELD", reason.equals("SCHEDULED") ? null : reason, effective)));
    when(store.findNextSchedule(eq(1L), any())).thenReturn(Optional.of(DATE));
    when(store.findScheduleViews(1, 1, 2)).thenReturn(new PageProjection<>(1, 2, 7, List.of(new ScheduleViewProjection(22, DATE.plusWeeks(1), "SKIPPED", null), new ScheduleViewProjection(21, DATE, "SCHEDULED", 11L))));
    when(store.findCommandHistory(1, 2, 3)).thenReturn(new PageProjection<>(2, 3, 9, List.of(new CommandHistoryProjection("PAUSE", "2026-08-28T23:59:59.123456+09:00"))));
    for (long id : List.of(10L, 11L, 12L)) when(store.findSnapshotItemDetails(id)).thenReturn(List.of(new SubscriptionItemDetailProjection(id + 100, "sku", id + 200, "product", null, 3)));
    when(store.findScheduleAddons(21)).thenReturn(List.of(
        new ScheduleAddonProjection(21, 201, 301, "addon", "addon sku", 3, new BigDecimal("1234.5600")),
        new ScheduleAddonProjection(21, 202, 302, "second", "second sku", 2, new BigDecimal("0.1000"))));
    when(store.scheduleAddonCount(21)).thenReturn(2);
    return store;
  }
}
