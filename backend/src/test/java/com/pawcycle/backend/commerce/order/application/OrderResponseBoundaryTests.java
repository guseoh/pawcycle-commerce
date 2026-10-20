package com.pawcycle.backend.commerce.order.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.order.api.*;
import com.pawcycle.backend.commerce.order.model.*;
import com.pawcycle.backend.commerce.order.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

class OrderResponseBoundaryTests {
  private final ObjectMapper json = new ObjectMapper();
  private final OrderPersistenceAdapter orders = mock(OrderPersistenceAdapter.class);
  private final QuickReorderPersistenceAdapter quick = mock(QuickReorderPersistenceAdapter.class);
  private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
  private final Instant now = Instant.parse("2026-10-18T15:00:00.123456Z");

  static Stream<Arguments> states() {
    var cases = new ArrayList<Arguments>();
    for (String order : List.of("PAID", "CREATED", "CANCELLED", "RETURNED"))
      for (String delivery : Arrays.asList(null, "PREPARING", "SHIPPED", "DELIVERED", "FAILED", "CANCELLED"))
        for (boolean cancellation : List.of(false, true))
          for (boolean returned : List.of(false, true))
            for (int delta : List.of(-1, 0, 1, 2)) cases.add(Arguments.of(order, delivery, cancellation, returned, delta));
    return cases.stream();
  }

  @ParameterizedTest @MethodSource("states")
  void frozenJsonActionsClockSamplingAndTimestampReferencesMatch(String status, String delivery,
      boolean cancelled, boolean returned, int delta) {
    Timestamp delivered = delta == 2 ? null : Timestamp.from(now.minus(Duration.ofDays(7)).plusNanos(delta * 1000L));
    var view = view(status, delivery, cancelled, returned, delivered);
    when(orders.findOrder(7, 9)).thenReturn(view);
    var oldClock = new CountingClock(now); var newClock = new CountingClock(now);
    var expected = frozen(oldClock).order(7, 9);
    var model = service(newClock).order(7, 9); var actual = OrderResponse.from(model);
    String actualJson = json.writeValueAsString(actual);
    assertThat(actualJson).isEqualTo(json.writeValueAsString(expected));
    var wire = json.readTree(actualJson);
    assertThat(wire.has("returnRequest")).isTrue();
    assertThat(wire.has("return")).isFalse();
    assertThat(wire.get("returnRequest").isNull()).isEqualTo(!returned);
    assertThat(actual).isEqualTo(expected);
    assertThat(newClock.samples).isEqualTo(oldClock.samples);
    assertThat(actual.createdAt()).isSameAs(view.createdAt());
    assertThat(actual.availableActions()).isSameAs(model.availableActions());
    assertThatThrownBy(() -> actual.items().add(null)).isInstanceOf(UnsupportedOperationException.class);
    if ("DELIVERED".equals(delivery) && !returned && delta != 2)
      assertThat(actual.availableActions().contains("REQUEST_RETURN")).isEqualTo(delta >= 0);
  }

  @Test void summariesAdminNullsScaleOrderingConstructorsAndNotFoundStayIdentical() {
    var stamp = Timestamp.from(now);
    when(orders.findOrders(7)).thenReturn(List.of(new OrderReadModel.Summary(9, null, "ONE_TIME", "PAID", new BigDecimal("123.40"), stamp, null),
        new OrderReadModel.Summary(2, "old", null, null, null, null, stamp)));
    assertThat(json.writeValueAsString(service(Clock.fixed(now, ZoneOffset.UTC)).orders(7).stream().map(OrderSummaryResponse::from).toList()))
        .isEqualTo(json.writeValueAsString(frozen(Clock.fixed(now, ZoneOffset.UTC)).orders(7)));
    var repository = mock(AdminOrderPersistenceAdapter.class);
    var admin = new AdminOrderQueryService(repository); var old = new FrozenAdminOrderQueryService(repository);
    var list = List.of(new OrderReadModel.AdminOrderView(9, null, 7, "PAID", new BigDecimal("123.40"), stamp),
        new OrderReadModel.AdminOrderView(1, "old", 7, null, null, null));
    when(repository.findAll()).thenReturn(list); when(repository.find(9)).thenReturn(list.getFirst());
    assertThat(admin.list().stream().map(AdminOrderResponse::from).toList()).isEqualTo(old.list());
    assertThat(AdminOrderResponse.from(admin.get(9))).isEqualTo(old.get(9));
    assertThatThrownBy(() -> admin.get(99)).isInstanceOfSatisfying(CommerceException.class,
        e -> { assertThat(e.status()).isEqualTo(404); assertThat(e.code()).isEqualTo("ORDER_NOT_FOUND"); });
    assertThatThrownBy(() -> service(Clock.systemUTC()).order(7, 99)).isInstanceOfSatisfying(CommerceException.class,
        e -> { assertThat(e.status()).isEqualTo(404); assertThat(e.code()).isEqualTo("ORDER_NOT_FOUND"); });
    var mutable = new ArrayList<OrderReorderResponse.Item>();
    var dto = new OrderReorderResponse(mutable, null, 1); mutable.add(new OrderReorderResponse.Item(9, 2));
    assertThat(dto.addedItems()).isSameAs(mutable).hasSize(1); assertThat(dto.skippedItems()).isNull();
  }

  @Test void reorderValidationResultOrderAndPostCommitConversionMatchFrozen() {
    var clock = Clock.fixed(now, ZoneOffset.UTC); var service = service(clock); var old = frozen(clock);
    for (String key : Arrays.asList(null, " ", "x".repeat(129))) {
      assertThatThrownBy(() -> service.reorder(7, 9, key)).isInstanceOfSatisfying(CommerceException.class,
          e -> { assertThat(e.status()).isEqualTo(400); assertThat(e.code()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED"); });
    }
    verifyNoInteractions(quick, tx);
    when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    var result = new QuickReorderPersistenceAdapter.ReorderResult(List.of(new QuickReorderPersistenceAdapter.ReorderItem(9, 2), new QuickReorderPersistenceAdapter.ReorderItem(3, 1)),
        List.of(new QuickReorderPersistenceAdapter.SkippedItem(5, 4, null)), 9007199254740991L);
    when(quick.reorder(7, 9, "key")).thenReturn(result);
    var expected = old.reorder(7, 9, "key"); clearInvocations(tx, quick);
    var model = service.reorder(7, 9, "key"); var actual = OrderReorderResponse.from(model);
    assertThat(actual).isEqualTo(expected); assertThat(json.writeValueAsString(actual)).isEqualTo(json.writeValueAsString(expected));
    var sequence = inOrder(tx, quick); sequence.verify(tx).getTransaction(any()); sequence.verify(quick).reorder(7, 9, "key"); sequence.verify(tx).commit(any());
    assertThatThrownBy(() -> actual.addedItems().add(null)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> actual.skippedItems().add(null)).isInstanceOf(UnsupportedOperationException.class);
    when(quick.reorder(7, 9, "bad")).thenThrow(new CommerceException(409, "IDEMPOTENCY_KEY_CONFLICT", "conflict"));
    assertThatThrownBy(() -> service.reorder(7, 9, "bad")).isInstanceOf(CommerceException.class);
    verify(tx).rollback(any());
  }

  @Test void malformedProjectionListsRetainTheOriginalExceptionClass() {
    var valid = view("PAID", null, false, false, null);
    var malformed = new OrderView(9, null, null, null, null, null, null, null, null, null, null, null, null,
        valid.createdAt(), null, null, null, null, null, null, List.of());
    when(orders.findOrder(7, 9)).thenReturn(malformed);
    assertThatThrownBy(() -> frozen(Clock.systemUTC()).order(7, 9)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> OrderResponse.from(service(Clock.systemUTC()).order(7, 9))).isInstanceOf(NullPointerException.class);
  }

  private OrderApplicationService service(Clock clock) { return new OrderApplicationService(orders, quick, tx, clock, 7); }
  private FrozenOrderApplicationService frozen(Clock clock) { return new FrozenOrderApplicationService(orders, quick, tx, clock, 7); }
  private OrderView view(String status, String delivery, boolean cancelled, boolean returned, Timestamp delivered) {
    Timestamp stamp = Timestamp.from(now);
    return new OrderView(9, "number", "ONE_TIME", status, new BigDecimal("123.40"), BigDecimal.ZERO, null,
        new BigDecimal("123.40"), null, "phone", null, "address", null, stamp, null,
        List.of(new OrderView.Item(9, "FULL", null, "product", null, new BigDecimal("61.70"), 2, new BigDecimal("123.40")),
            new OrderView.Item(1, "LEGACY_PARTIAL", null, null, null, null, 1, null)),
        new OrderView.Payment(12, "NORMAL", "TOSS", "READY", new BigDecimal("123.40"), 3, null),
        delivery == null ? null : new OrderView.Delivery(13, 9, delivery, null, null, "failure", stamp, delivered, null, null),
        cancelled ? new OrderView.Cancellation(14, "REQUESTED", null, stamp, null) : null,
        returned ? new OrderView.ReturnRequest(15, "REQUESTED", null, null, null, stamp, null, null) : null,
        List.of(new OrderView.Refund(17, "CANCELLATION", "READY", BigDecimal.ONE, 1, 0), new OrderView.Refund(16, "RETURN", "READY", null, 2, 1)));
  }
  private static final class CountingClock extends Clock {
    private final Instant value; private int samples;
    CountingClock(Instant value) { this.value = value; }
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { samples++; return value; }
  }
}
