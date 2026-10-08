package com.pawcycle.backend.commerce.operations.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.metrics.persistence.CommerceMetricsQueryRepository;
import com.pawcycle.backend.commerce.operations.application.OperationsQueryService;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OperationsReadModelIntegrationTests {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private OperationsQueryRepository queries;
  @Autowired private OperationsQueryService service;
  @Autowired private CommerceMetricsQueryRepository metrics;
  private SecondaryReadFixtures fixtures;
  private LegacyOperationsQueryRepository legacy;
  private long memberId;
  private Sku sku;

  @BeforeEach
  void setUp() {
    fixtures = new SecondaryReadFixtures(jdbc, entities);
    legacy = new LegacyOperationsQueryRepository(jdbc);
    memberId = fixtures.member();
    var product = fixtures.product(fixtures.category(true), fixtures.brand(true), "DOG", "PUBLIC");
    sku = fixtures.sku(product, SkuStatus.ACTIVE, 10);
  }

  @Test
  void allSixteenTypesKeepProjectionNullAttemptDateCoercionOrderingAndActions() {
    long pendingBefore = metrics.countPendingOperations();
    Map<String, Long> expected = new HashMap<>();
    for (String state : List.of("PREPARING", "SHIPPED", "FAILED")) {
      long order = order("PAID");
      jdbc.update("INSERT INTO deliveries(order_id,status,shipped_at,failed_at) VALUES (?,?,?,?)", order, state,
          state.equals("PREPARING") ? null : SecondaryReadFixtures.stamp(),
          state.equals("FAILED") ? SecondaryReadFixtures.stamp() : null);
      expected.put("DELIVERY_" + state, fixtures.lastId());
    }
    for (String state : List.of("REQUESTED", "APPROVED")) {
      jdbc.update("INSERT INTO order_returns(order_id,status,reason,requested_at,decided_at) VALUES (?,?,'fixture',?,?)", order("PAID"), state,
          SecondaryReadFixtures.stamp(), state.equals("REQUESTED") ? null : SecondaryReadFixtures.stamp());
      expected.put("RETURN_" + state, fixtures.lastId());
    }
    for (String state : List.of("READY", "PROCESSING", "UNKNOWN", "FAILED")) {
      long order = order("PAID");
      expected.put("REFUND_" + state, fixtures.refund(order, fixtures.cancellation(order), state, 2));
    }
    expected.put("PAYMENT_UNKNOWN", fixtures.payment(order("PAID"), "NORMAL", "UNKNOWN", 1));
    expected.put("PAYMENT_PROCESSING", fixtures.payment(order("PAID"), "BILLING", "PROCESSING", 1));
    expected.put("PAYMENT_ACTION_REQUIRED", order("PAYMENT_ACTION_REQUIRED"));
    long subscription = subscription();
    int day = 20;
    for (String reason : List.of("MISSING_SHIPPING_ADDRESS", "MISSING_BILLING_METHOD", "PAYMENT_RETRY_EXHAUSTED", "PAYMENT_RETRY_STOCK_UNAVAILABLE")) {
      long schedule = schedule(subscription, reason, "2026-09-" + day++);
      if (reason.equals("PAYMENT_RETRY_STOCK_UNAVAILABLE")) {
        long order = order("PAYMENT_ACTION_REQUIRED");
        jdbc.update("INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,scheduled_date) VALUES (?,?,?,'2026-09-30')", order, subscription, schedule);
        fixtures.payment(order, "BILLING", "FAILED", 1);
        long latest = fixtures.payment(order, "BILLING", "FAILED", 3);
        expected.put(reason, latest);
      } else expected.put(reason, schedule);
    }
    // Freeze CURRENT_TIMESTAMP(6) for both JDBC implementations on this transaction connection.
    assertThat(expected).hasSize(16);
    // The cached metrics source counts REQUESTED returns, READY/FAILED/UNKNOWN refunds and UNKNOWN payments.
    assertThat(metrics.countPendingOperations()).isEqualTo(pendingBefore + 5);
    jdbc.execute("SET timestamp=" + SecondaryReadFixtures.stamp().toLocalDateTime().toEpochSecond(java.time.ZoneOffset.UTC) + ".123456");
    try {
      var actual = queries.findPending();
      assertThat(actual).isEqualTo(legacy.findPending());
      for (var entry : expected.entrySet()) assertThat(actual).anySatisfy(row -> {
        assertThat(row.type()).isEqualTo(entry.getKey());
        assertThat(row.referenceId()).isEqualTo(entry.getValue());
        assertThat(row.createdAt()).isNotNull();
        Integer expectedAttempt = null;
        if (row.type().startsWith("REFUND_")) expectedAttempt = 2;
        else if (row.type().equals("PAYMENT_RETRY_STOCK_UNAVAILABLE")) expectedAttempt = 3;
        assertThat(row.attemptNo()).isEqualTo(expectedAttempt);
      });
      assertThat(actual).extracting(OperationsQueryRepository.PendingRow::createdAt)
          .isSortedAccordingTo(Comparator.nullsLast(Comparator.reverseOrder()));
      var retry = actual.stream().filter(row -> row.referenceId() == expected.get("PAYMENT_RETRY_STOCK_UNAVAILABLE") && row.type().equals("PAYMENT_RETRY_STOCK_UNAVAILABLE")).findFirst().orElseThrow();
      assertThat(retry.createdAt()).isEqualTo(Timestamp.valueOf("2026-09-23 00:00:00"));
      var referenceService = new OperationsQueryService(new OperationsQueryRepository(jdbc) {
        @Override public List<PendingRow> findPending() { return legacy.findPending(); }
      });
      assertThat(service.pending()).isEqualTo(referenceService.pending());
    } finally { jdbc.execute("SET timestamp=0"); }
  }

  @Test
  void newerRefundAttemptsAndLatestPaymentAttemptKeepTheirExclusionMeaning() {
    long order = order("PAID");
    long cancellation = fixtures.cancellation(order);
    long failed = fixtures.refund(order, cancellation, "FAILED", 1);
    long newer = fixtures.refund(order, cancellation, "READY", 2);
    long heldOrder = order("PAYMENT_ACTION_REQUIRED");
    long subscription = subscription();
    long schedule = schedule(subscription, "PAYMENT_RETRY_STOCK_UNAVAILABLE", "2026-09-30");
    jdbc.update("INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,scheduled_date) VALUES (?,?,?,'2026-09-30')", heldOrder, subscription, schedule);
    long first = fixtures.payment(heldOrder, "BILLING", "FAILED", 1);
    long latest = fixtures.payment(heldOrder, "BILLING", "FAILED", 2);
    assertThat(queries.findPending()).noneSatisfy(row -> {
      assertThat(row.type()).isEqualTo("REFUND_FAILED"); assertThat(row.referenceId()).isEqualTo(failed);
    }).anySatisfy(row -> {
      assertThat(row.type()).isEqualTo("REFUND_READY"); assertThat(row.referenceId()).isEqualTo(newer);
    }).anySatisfy(row -> {
      assertThat(row.type()).isEqualTo("PAYMENT_RETRY_STOCK_UNAVAILABLE"); assertThat(row.referenceId()).isEqualTo(latest);
    }).noneSatisfy(row -> {
      assertThat(row.type()).isEqualTo("PAYMENT_RETRY_STOCK_UNAVAILABLE"); assertThat(row.referenceId()).isEqualTo(first);
    });
    fixtures.payment(heldOrder, "BILLING", "READY", 3);
    assertThat(queries.findPending()).noneSatisfy(row -> {
      assertThat(row.type()).isEqualTo("PAYMENT_RETRY_STOCK_UNAVAILABLE"); assertThat(row.referenceId()).isEqualTo(latest);
    });
    // Other integration tests may leave PREPARING deliveries whose timestamp is evaluated per SQL call.
    jdbc.execute("SET timestamp=" + SecondaryReadFixtures.stamp().toLocalDateTime().toEpochSecond(java.time.ZoneOffset.UTC) + ".123456");
    try { assertThat(queries.findPending()).isEqualTo(legacy.findPending()); }
    finally { jdbc.execute("SET timestamp=0"); }
  }

  @Test
  void currentTimestampFallbackAndNullCreatedAtKeepLegacyValues() {
    long preparingOrder = order("PAID");
    jdbc.update("INSERT INTO deliveries(order_id,status) VALUES (?,'PREPARING')", preparingOrder);
    long preparing = fixtures.lastId();
    long refundOrder = order("PAID");
    long processing = fixtures.refund(refundOrder, fixtures.cancellation(refundOrder), "PROCESSING", 2);
    jdbc.update("UPDATE refunds SET processed_at=NULL WHERE id=?", processing);
    jdbc.execute("SET timestamp=" + SecondaryReadFixtures.stamp().toLocalDateTime().toEpochSecond(java.time.ZoneOffset.UTC) + ".123456");
    try {
      assertThat(queries.findPending()).isEqualTo(legacy.findPending()).anySatisfy(row -> {
        assertThat(row.type()).isEqualTo("DELIVERY_PREPARING"); assertThat(row.referenceId()).isEqualTo(preparing);
        assertThat(row.createdAt()).isEqualTo(SecondaryReadFixtures.stamp());
        assertThat(row.createdAt().getNanos()).isEqualTo(123456000);
      }).anySatisfy(row -> {
        assertThat(row.type()).isEqualTo("REFUND_PROCESSING"); assertThat(row.referenceId()).isEqualTo(processing);
        assertThat(row.createdAt()).isNull(); assertThat(row.attemptNo()).isEqualTo(2);
      });
    } finally { jdbc.execute("SET timestamp=0"); }
  }

  private long order(String status) { return fixtures.order(memberId, "ONE_TIME", status, new BigDecimal("123.45"), SecondaryReadFixtures.stamp()); }

  private long subscription() {
    jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date) VALUES (?,?,1,4,'2026-09-01','2026-09-30')", memberId, sku.getId());
    return fixtures.lastId();
  }

  private long schedule(long subscription, String reason, String date) {
    jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,hold_reason) VALUES (?,?,'HELD',?)", subscription, date, reason);
    return fixtures.lastId();
  }
}
