package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.cancellation.application.CancellationService;
import com.pawcycle.backend.commerce.cancellation.persistence.CancellationPersistenceAdapter;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.inventory.application.InventoryService;
import com.pawcycle.backend.commerce.notification.application.NotificationService;
import com.pawcycle.backend.commerce.returning.application.ReturnService;
import com.pawcycle.backend.commerce.returning.api.ReturnResponse;
import com.pawcycle.backend.commerce.returning.persistence.ReturnPersistenceAdapter;
import com.pawcycle.backend.support.AfterSalesFixtures;
import com.pawcycle.backend.support.AfterSalesFixtures.OrderFixture;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AfterSalesConcurrencyIntegrationTests {
  @Autowired private CancellationService cancellations;
  @Autowired private ReturnService returns;
  @Autowired private NotificationService notifications;
  @Autowired private InventoryService inventory;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private PlatformTransactionManager manager;
  @MockitoSpyBean private CancellationPersistenceAdapter cancellationRows;
  @MockitoSpyBean private ReturnPersistenceAdapter returnRows;
  @MockitoSpyBean private AdminAuditService audits;
  private AfterSalesFixtures f;

  @BeforeEach void fixtures() { f = new AfterSalesFixtures(jdbc, entities, manager); }
  private OrderFixture order(String delivery) { return f.paidOrder(delivery, new BigDecimal("123.45")); }

  @Test
  void sameOrderCancellationRaceReplaysOneRefundAndOneRestorationPerSku() throws Exception {
    OrderFixture o = order("PREPARING");
    var results = AfterSalesFixtures.race(
        () -> cancellations.request(o.member(), o.order(), "cancel"),
        () -> cancellations.request(o.member(), o.order(), "cancel"));
    assertThat(results).allSatisfy(r -> assertThat(r.error()).isNull());
    assertThat(results.get(0).value()).isEqualTo(results.get(1).value());
    assertThat(f.integer("SELECT COUNT(*) FROM order_cancellations WHERE order_id=?", o.order())).isEqualTo(1);
    assertThat(f.integer("SELECT COUNT(*) FROM refunds WHERE order_id=?", o.order())).isEqualTo(1);
    assertRestored(o, "CANCEL_RESTORE");
    assertThat(f.text("SELECT status FROM deliveries WHERE order_id=?", o.order())).isEqualTo("CANCELLED");
    assertThat(jdbc.queryForObject("SELECT amount FROM refunds WHERE order_id=?", BigDecimal.class, o.order())).isEqualByComparingTo("123.45");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PREPARING", "DELIVERED"})
  void cancellationVersusReturnUsesSameOrderLockAndDeliveryEligibility(String delivery) throws Exception {
    OrderFixture o = order(delivery);
    var results = AfterSalesFixtures.race(
        () -> cancellations.request(o.member(), o.order(), "cancel race"),
        () -> returns.request(o.member(), o.order(), "return race"));
    assertThat(results.stream().filter(r -> r.error() == null).count()).isEqualTo(1);
    assertThat(results.stream().filter(r -> r.error() != null).toList()).singleElement().satisfies(r -> {
      assertThat(r.error().status()).isEqualTo(409);
      assertThat(r.error().code()).isEqualTo("PREPARING".equals(delivery) ? "RETURN_NOT_ALLOWED" : "CANCELLATION_NOT_ALLOWED");
    });
    boolean cancel = "PREPARING".equals(delivery);
    assertThat(f.integer("SELECT COUNT(*) FROM order_cancellations WHERE order_id=?", o.order())).isEqualTo(cancel ? 1 : 0);
    assertThat(f.integer("SELECT COUNT(*) FROM order_returns WHERE order_id=?", o.order())).isEqualTo(cancel ? 0 : 1);
    assertThat(f.integer("SELECT COUNT(*) FROM refunds WHERE order_id=?", o.order())).isEqualTo(cancel ? 1 : 0);
    assertThat(f.integer("SELECT available_quantity FROM inventories WHERE sku_id=?", o.firstSku())).isEqualTo(cancel ? 12 : 10);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SHIPPED", "PAYMENT_FAILED", "NO_SUCCESS_PAYMENT"})
  void invalidCancellationDoesNotRestoreOrCreateRefund(String scenario) {
    OrderFixture o = order("SHIPPED".equals(scenario) ? "SHIPPED" : "PREPARING");
    if ("PAYMENT_FAILED".equals(scenario)) jdbc.update("UPDATE orders SET status='PAYMENT_FAILED' WHERE id=?", o.order());
    if ("NO_SUCCESS_PAYMENT".equals(scenario)) jdbc.update("UPDATE payments SET status='FAILED' WHERE id=?", o.payment());
    assertError(() -> cancellations.request(o.member(), o.order(), "invalid"), "CANCELLATION_NOT_ALLOWED");
    assertNoCompensation(o);
  }

  @Test
  void failureAfterRefundInsertRollsBackCancellationDeliveryAndInventory() {
    OrderFixture o = order("PREPARING");
    doAnswer(invocation -> {
      invocation.callRealMethod();
      throw new IllegalStateException("after refund insert");
    }).when(cancellationRows).createRefund(org.mockito.ArgumentMatchers.eq(o.order()), org.mockito.ArgumentMatchers.anyLong());
    assertThatThrownBy(() -> cancellations.request(o.member(), o.order(), "rollback"))
        .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
        .hasRootCauseInstanceOf(IllegalStateException.class).hasRootCauseMessage("after refund insert");
    assertNoCompensation(o);
    assertThat(f.integer("SELECT COUNT(*) FROM order_cancellations WHERE order_id=?", o.order())).isZero();
    assertThat(f.text("SELECT status FROM deliveries WHERE order_id=?", o.order())).isEqualTo("PREPARING");
    assertThat(jdbc.queryForObject("SELECT cancelled_at FROM deliveries WHERE order_id=?", Timestamp.class, o.order())).isNull();
  }

  @Test
  void simultaneousReturnRequestsReplayNullableProjectionWithoutCompensation() throws Exception {
    OrderFixture o = order("DELIVERED");
    var results = AfterSalesFixtures.race(
        () -> returns.request(o.member(), o.order(), "return"),
        () -> returns.request(o.member(), o.order(), "return"));
    assertThat(results).allSatisfy(r -> assertThat(r.error()).isNull());
    assertThat(results.get(0).value()).isEqualTo(results.get(1).value());
    ReturnResponse response = (ReturnResponse) results.get(0).value();
    assertThat(response.restock()).isNull();
    assertThat(response.decidedAt()).isNull();
    assertThat(response.receivedAt()).isNull();
    assertThat(f.integer("SELECT COUNT(*) FROM order_returns WHERE order_id=?", o.order())).isEqualTo(1);
    assertNoCompensation(o);
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0, 1})
  void returnWindowIsInclusiveToOneMicrosecondAtDeadline(long offsetMicros) {
    OrderFixture o = order("DELIVERED");
    Instant now = Instant.parse("2026-10-08T00:00:00.123456Z");
    jdbc.update("UPDATE deliveries SET delivered_at=? WHERE order_id=?", Timestamp.from(now.minusSeconds(7 * 86400).plusNanos(offsetMicros * 1000)), o.order());
    var boundary = new ReturnService(returnRows, manager, notifications, audits, inventory, 7, Clock.fixed(now, ZoneOffset.UTC));
    if (offsetMicros < 0) assertError(() -> boundary.request(o.member(), o.order(), "expired"), "RETURN_NOT_ALLOWED");
    else assertThat(boundary.request(o.member(), o.order(), "boundary").status()).isEqualTo("REQUESTED");
    assertNoCompensation(o);
  }

  @Test
  void competingApproveAndRejectCommitExactlyOneDecisionNotificationAndAudit() throws Exception {
    OrderFixture o = order("DELIVERED");
    long id = returns.request(o.member(), o.order(), "decision").returnId();
    var results = AfterSalesFixtures.race(() -> returns.approve(o.admin(), id), () -> returns.reject(o.admin(), id, "rejected"));
    assertThat(results.stream().filter(r -> r.error() == null).count()).isEqualTo(1);
    assertThat(results.stream().filter(r -> r.error() != null).toList()).singleElement().satisfies(r -> assertThat(r.error().code()).isEqualTo("RETURN_STATE_CONFLICT"));
    assertThat(f.integer("SELECT COUNT(*) FROM notifications WHERE member_id=? AND reference_type='RETURN' AND reference_id=?", o.member(), id)).isEqualTo(1);
    assertThat(f.integer("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND target_type='RETURN' AND target_id=?", o.admin(), id)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void concurrentReceiveCreatesOneRefundAndRestoresOnlyWhenRequested(boolean restock) throws Exception {
    OrderFixture o = order("DELIVERED");
    long id = returns.request(o.member(), o.order(), "receive").returnId();
    returns.approve(o.admin(), id);
    var results = AfterSalesFixtures.race(() -> returns.receive(o.admin(), id, restock), () -> returns.receive(o.admin(), id, restock));
    assertThat(results.stream().filter(r -> r.error() == null).count()).isEqualTo(1);
    assertThat(results.stream().filter(r -> r.error() != null).toList()).singleElement().satisfies(r -> assertThat(r.error().code()).isEqualTo("RETURN_STATE_CONFLICT"));
    assertThat(f.integer("SELECT COUNT(*) FROM refunds WHERE order_id=? AND return_id=?", o.order(), id)).isEqualTo(1);
    assertThat(f.integer("SELECT available_quantity FROM inventories WHERE sku_id=?", o.firstSku())).isEqualTo(restock ? 12 : 10);
    assertThat(f.integer("SELECT COUNT(*) FROM inventory_movements WHERE return_id=?", id)).isEqualTo(restock ? 2 : 0);
    assertThat(f.integer("SELECT COUNT(*) FROM admin_audit_logs WHERE target_id=? AND action='RETURN_RECEIVE'", id)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT restock FROM order_returns WHERE id=?", Boolean.class, id)).isEqualTo(restock);
  }

  @Test
  void decisionAuditFailureRollsBackStateAndAlreadyInsertedNotification() {
    OrderFixture o = order("DELIVERED");
    long id = returns.request(o.member(), o.order(), "audit rollback").returnId();
    doThrow(new IllegalStateException("audit failure")).when(audits).append(o.admin(), "RETURN_APPROVE", "RETURN", id);
    assertThatThrownBy(() -> returns.approve(o.admin(), id)).isInstanceOf(IllegalStateException.class);
    assertThat(f.text("SELECT status FROM order_returns WHERE id=?", id)).isEqualTo("REQUESTED");
    assertThat(jdbc.queryForObject("SELECT decided_at FROM order_returns WHERE id=?", Timestamp.class, id)).isNull();
    assertThat(f.integer("SELECT COUNT(*) FROM notifications WHERE member_id=? AND reference_type='RETURN' AND reference_id=?", o.member(), id)).isZero();
  }

  @Test
  void receiveAuditFailureRollsBackRefundInventoryMovementAndReceiveFields() {
    OrderFixture o = order("DELIVERED");
    long id = returns.request(o.member(), o.order(), "receive rollback").returnId();
    returns.approve(o.admin(), id);
    doThrow(new IllegalStateException("receive audit failure")).when(audits).append(o.admin(), "RETURN_RECEIVE", "RETURN", id);
    assertThatThrownBy(() -> returns.receive(o.admin(), id, true)).isInstanceOf(IllegalStateException.class);
    assertThat(f.text("SELECT status FROM order_returns WHERE id=?", id)).isEqualTo("APPROVED");
    assertThat(jdbc.queryForObject("SELECT restock FROM order_returns WHERE id=?", Boolean.class, id)).isNull();
    assertThat(jdbc.queryForObject("SELECT received_at FROM order_returns WHERE id=?", Timestamp.class, id)).isNull();
    assertNoCompensation(o);
  }

  private void assertRestored(OrderFixture o, String type) {
    assertThat(f.integer("SELECT available_quantity FROM inventories WHERE sku_id=?", o.firstSku())).isEqualTo(12);
    assertThat(f.integer("SELECT available_quantity FROM inventories WHERE sku_id=?", o.secondSku())).isEqualTo(11);
    assertThat(f.integer("SELECT reserved_quantity FROM inventories WHERE sku_id=?", o.firstSku())).isZero();
    assertThat(f.integer("SELECT COUNT(*) FROM inventory_movements WHERE type=? AND sku_id IN (?,?)", type, o.firstSku(), o.secondSku())).isEqualTo(2);
    assertThat(jdbc.queryForList("SELECT sku_id FROM inventory_movements WHERE sku_id IN (?,?) ORDER BY id", Long.class, o.firstSku(), o.secondSku())).containsExactly(o.firstSku(), o.secondSku());
  }
  private void assertNoCompensation(OrderFixture o) {
    assertThat(f.integer("SELECT available_quantity FROM inventories WHERE sku_id=?", o.firstSku())).isEqualTo(10);
    assertThat(f.integer("SELECT version FROM inventories WHERE sku_id=?", o.firstSku())).isZero();
    assertThat(f.integer("SELECT COUNT(*) FROM inventory_movements WHERE sku_id IN (?,?)", o.firstSku(), o.secondSku())).isZero();
    assertThat(f.integer("SELECT COUNT(*) FROM refunds WHERE order_id=?", o.order())).isZero();
  }
  private void assertError(Runnable action, String code) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(CommerceException.class, e -> assertThat(e.code()).isEqualTo(code));
  }
}
