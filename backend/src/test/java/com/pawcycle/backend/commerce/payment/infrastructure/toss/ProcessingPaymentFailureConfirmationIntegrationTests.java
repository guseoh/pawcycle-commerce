package com.pawcycle.backend.commerce.payment.infrastructure.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentFailureConfirmationService;
import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentFailureConfirmationService.Outcome;
import com.pawcycle.backend.commerce.payment.application.TossPaymentAdapter;
import com.pawcycle.backend.commerce.payment.persistence.PaymentReconciliationPersistenceAdapter;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #417 Phase 2b (PWC-0134) on disposable MySQL with the real Toss query parser behind a fake HTTP
 * server. Recovery must never call the provider confirm endpoint, and a stop or refusal must leave
 * payment, order, inventory, ledger, cart, coupon, notifications and audit untouched.
 */
@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class ProcessingPaymentFailureConfirmationIntegrationTests {
  private static final List<String> ACTIONS =
      List.of("PAYMENT_PROCESSING_FAILED_ABORTED", "PAYMENT_PROCESSING_FAILED_EXPIRED");

  @Autowired ProcessingPaymentFailureConfirmationService confirmation;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @MockitoSpyBean PlatformTransactionManager manager;
  @MockitoBean TossPaymentAdapter provider;
  @MockitoSpyBean AdminAuditService audits;
  @MockitoSpyBean PaymentReconciliationPersistenceAdapter persistence;
  TossPaymentQuerySafetyTests.FakeServer fake;
  volatile CountDownLatch queryEntered;
  volatile CountDownLatch queryRelease;
  /** When set, the next commit that persisted this payment's audit row reports a failure afterwards. */
  volatile Long loseCommitAckFor;

  @BeforeEach
  void start() throws Exception {
    fake = new TossPaymentQuerySafetyTests.FakeServer();
    TossPaymentAdapter real = fake.adapter();
    when(provider.isConfigured()).thenReturn(true);
    when(provider.queryPayment(anyString(), any(), any()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              if (queryEntered != null) {
                queryEntered.countDown();
                assertThat(queryRelease.await(30, TimeUnit.SECONDS)).isTrue();
              }
              return real.queryPayment(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            });
    doAnswer(
            call -> {
              TransactionStatus status = call.getArgument(0);
              call.callRealMethod();
              Long target = loseCommitAckFor;
              // Only a physical commit; participating inner commits (e.g. the audit append) are not.
              if (status.isNewTransaction() && target != null && auditRows(target) > 0) {
                loseCommitAckFor = null;
                throw new TransactionSystemException("injected: commit acknowledgement lost");
              }
              return null;
            })
        .when(manager)
        .commit(any());
  }

  @AfterEach
  void stop() {
    fake.close();
    verify(provider, never()).confirm(anyString(), anyString(), any());
    loseCommitAckFor = null;
    queryEntered = null;
  }

  @ParameterizedTest
  @ValueSource(strings = {"ABORTED", "EXPIRED"})
  void f1ProviderFailureOfSamePaymentReleasesReservationAndFailsPaymentAndOrderOnce(String remote) {
    Fixture f = fixture(2);
    provider(f, remote);

    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.FAILURE_CONFIRMED);

    assertThat(jdbc.queryForMap("SELECT status,provider_status,failure_code FROM payments WHERE id=?", f.payment()))
        .containsEntry("status", "FAILED")
        .containsEntry("provider_status", remote)
        .containsEntry("failure_code", "RECOVERED_PROVIDER_FAILED");
    assertThat(one("SELECT status FROM orders WHERE id=?", f.order())).isEqualTo("PAYMENT_FAILED");
    for (int i = 0; i < f.skus().size(); i++) {
      long sku = f.skus().get(i);
      int quantity = i + 2;
      assertThat(jdbc.queryForMap("SELECT available_quantity,reserved_quantity FROM inventories WHERE sku_id=?", sku))
          .containsEntry("available_quantity", 10)
          .containsEntry("reserved_quantity", 0);
      assertThat(count("SELECT quantity FROM inventory_movements WHERE payment_id=? AND type='RELEASE' AND sku_id=?", f.payment(), sku))
          .isEqualTo(quantity);
    }
    assertThat(count("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=? AND type='DEDUCT'", f.payment())).isZero();
    assertThat(jdbc.queryForMap("SELECT status,reserved_order_id FROM member_coupons WHERE id=?", f.coupon()))
        .containsEntry("status", "AVAILABLE")
        .containsEntry("reserved_order_id", null);
    assertThat(jdbc.queryForList("SELECT sku_id,quantity FROM cart_items WHERE cart_id=? ORDER BY sku_id", f.cart()))
        .hasSize(1).first().satisfies(row -> assertThat(row).containsEntry("quantity", 5));
    assertThat(count("SELECT version FROM carts WHERE id=?", f.cart())).isEqualTo(7);
    assertThat(count("SELECT COUNT(*) FROM notifications WHERE member_id=?", f.member())).isZero();
    assertThat(count("SELECT COUNT(*) FROM deliveries WHERE order_id=?", f.order())).isZero();
    assertThat(jdbc.queryForList("SELECT admin_id,action,target_type FROM admin_audit_logs WHERE target_id=? AND action IN (?,?)",
            f.payment(), ACTIONS.get(0), ACTIONS.get(1)))
        .singleElement()
        .satisfies(row -> assertThat(row)
            .containsEntry("admin_id", f.admin())
            .containsEntry("action", "PAYMENT_PROCESSING_FAILED_" + remote)
            .containsEntry("target_type", "PAYMENT"));
    assertThat(fake.requests.get()).isEqualTo(1);
  }

  @Test
  void f2InspectModeOnlyReportsAndWritesNothing() {
    Fixture f = fixture(1);
    provider(f, "ABORTED");
    var before = state(f);
    assertThat(confirmation.inspect(f.payment(), f.admin())).isEqualTo(Outcome.INSPECTED_CONFIRMABLE);
    assertThat(state(f)).isEqualTo(before);
    assertThat(fake.requests.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({
    "DONE,STOPPED_PROVIDER_DONE",
    "IN_PROGRESS,STOPPED_PROVIDER_UNKNOWN",
    "READY,STOPPED_PROVIDER_UNKNOWN",
    "WAITING_FOR_DEPOSIT,STOPPED_PROVIDER_UNKNOWN",
    "CANCELED,STOPPED_PROVIDER_UNKNOWN",
    "PARTIAL_CANCELED,STOPPED_PROVIDER_UNKNOWN"
  })
  void f3ProviderStatesOtherThanAbortedOrExpiredStopWithoutChangingAnything(String remote, Outcome expected) {
    Fixture f = fixture(1);
    provider(f, remote);
    var before = state(f);
    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(expected);
    assertThat(state(f)).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 404, 500, 503})
  void f4ProviderHttpErrorsAndNotFoundStopWithoutChangingAnything(int status) {
    Fixture f = fixture(1);
    fake.respond(status, "{\"code\":\"QUERY_ERROR\"}");
    var before = state(f);
    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.STOPPED_PROVIDER_UNKNOWN);
    assertThat(state(f)).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"order", "key", "amount"})
  void f5ProviderFailureOfAnotherPaymentStops(String mismatch) {
    Fixture f = fixture(1);
    fake.respond(
        200,
        TossPaymentQuerySafetyTests.FakeServer.payment(
            mismatch.equals("order") ? "other-order" : f.identity(),
            mismatch.equals("key") ? "other-key" : "key-1",
            mismatch.equals("amount") ? "999.99" : "123.45",
            "ABORTED"));
    var before = state(f);
    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.STOPPED_PROVIDER_UNKNOWN);
    assertThat(state(f)).isEqualTo(before);
  }

  @ParameterizedTest
  @CsvSource({
    "BILLING,PROCESSING,PAYMENT_PENDING,,REFUSED_NOT_NORMAL",
    "NORMAL,READY,PAYMENT_PENDING,,REFUSED_NOT_PROCESSING",
    "NORMAL,UNKNOWN,PAYMENT_PENDING,,REFUSED_NOT_PROCESSING",
    "NORMAL,FAILED,PAYMENT_PENDING,,REFUSED_NOT_PROCESSING",
    "NORMAL,PROCESSING,PAYMENT_FAILED,,REFUSED_ORDER_NOT_PENDING",
    "NORMAL,PROCESSING,PAYMENT_PENDING,no-key,REFUSED_IDENTITY_MISSING",
    "NORMAL,PROCESSING,PAYMENT_PENDING,recent,REFUSED_TOO_RECENT",
    "NORMAL,PROCESSING,PAYMENT_PENDING,no-expiry,REFUSED_TOO_RECENT",
    "NORMAL,PROCESSING,PAYMENT_PENDING,sibling-succeeded,REFUSED_ORDER_HAS_SUCCEEDED_PAYMENT",
    "NORMAL,PROCESSING,PAYMENT_PENDING,no-reserve,REFUSED_RESERVATION_MISMATCH",
    "NORMAL,PROCESSING,PAYMENT_PENDING,partial-reserve,REFUSED_RESERVATION_MISMATCH",
    "NORMAL,PROCESSING,PAYMENT_PENDING,quantity-mismatch,REFUSED_RESERVATION_MISMATCH",
    "NORMAL,PROCESSING,PAYMENT_PENDING,released,REFUSED_RESERVATION_MISMATCH",
    "NORMAL,PROCESSING,PAYMENT_PENDING,deducted,REFUSED_RESERVATION_MISMATCH"
  })
  void f6IneligiblePaymentsAreRefusedBeforeProviderIo(
      String type, String status, String orderStatus, String variant, Outcome expected) {
    Fixture f = fixture(type, status, orderStatus, 2);
    long first = f.skus().get(0);
    if ("no-key".equals(variant)) jdbc.update("UPDATE payments SET payment_key=NULL WHERE id=?", f.payment());
    if ("recent".equals(variant))
      jdbc.update("UPDATE payments SET expires_at=? WHERE id=?", Timestamp.from(Instant.now().minusSeconds(60)), f.payment());
    if ("no-expiry".equals(variant)) jdbc.update("UPDATE payments SET expires_at=NULL WHERE id=?", f.payment());
    if ("sibling-succeeded".equals(variant)) {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      new TransactionTemplate(manager).executeWithoutResult(tx -> fixtures.payment(f.order(), "NORMAL", "SUCCEEDED", 2));
    }
    if ("no-reserve".equals(variant)) jdbc.update("DELETE FROM inventory_movements WHERE payment_id=?", f.payment());
    if ("partial-reserve".equals(variant))
      jdbc.update("DELETE FROM inventory_movements WHERE payment_id=? AND sku_id=?", f.payment(), first);
    if ("quantity-mismatch".equals(variant))
      jdbc.update("UPDATE inventory_movements SET quantity=1 WHERE payment_id=? AND sku_id=?", f.payment(), first);
    if ("released".equals(variant)) movement(f.payment(), first, "RELEASE", 2);
    if ("deducted".equals(variant)) movement(f.payment(), first, "DEDUCT", 2);
    provider(f, "ABORTED");
    var before = state(f);
    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(expected);
    assertThat(state(f)).isEqualTo(before);
    assertThat(fake.requests.get()).isZero();
  }

  @Test
  void f7NonAdminOperatorAndUnconfiguredProviderAreRefused() {
    Fixture f = fixture(1);
    provider(f, "ABORTED");
    var before = state(f);
    assertThat(confirmation.confirm(f.payment(), f.member())).isEqualTo(Outcome.REFUSED_OPERATOR_NOT_ADMIN);
    when(provider.isConfigured()).thenReturn(false);
    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.REFUSED_PROVIDER_UNAVAILABLE);
    assertThat(state(f)).isEqualTo(before);
    assertThat(fake.requests.get()).isZero();
  }

  @Test
  void f8RerunAfterConfirmationReturnsAlreadyConfirmedWithoutProviderCallOrDuplicateEffects() {
    Fixture f = fixture(1);
    provider(f, "EXPIRED");
    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.FAILURE_CONFIRMED);
    var after = state(f);
    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.ALREADY_CONFIRMED);
    assertThat(confirmation.inspect(f.payment(), f.admin())).isEqualTo(Outcome.ALREADY_CONFIRMED);
    assertThat(state(f)).isEqualTo(after);
    assertThat(fake.requests.get()).isEqualTo(1);
  }

  @Test
  void f9TwoConcurrentConfirmationsApplyEffectsOnce() throws Exception {
    Fixture f = fixture(2);
    provider(f, "ABORTED");
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    List<Outcome> outcomes = new ArrayList<>();
    try {
      List<Future<Outcome>> runs = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        runs.add(executor.submit(() -> {
          start.await();
          return confirmation.confirm(f.payment(), f.admin());
        }));
      start.countDown();
      for (var run : runs) outcomes.add(run.get(60, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
    assertThat(outcomes).containsExactlyInAnyOrder(Outcome.FAILURE_CONFIRMED, Outcome.ALREADY_CONFIRMED);
    assertThat(count("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=? AND type='RELEASE'", f.payment())).isEqualTo(2);
    assertThat(auditRows(f.payment())).isEqualTo(1);
    for (long sku : f.skus())
      assertThat(count("SELECT reserved_quantity FROM inventories WHERE sku_id=?", sku)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCEEDED", "FAILED", "UNKNOWN"})
  void f10NormalWorkerFinalizingDuringProviderQueryWinsAndConfirmationAppliesNothing(String normalResult) throws Exception {
    Fixture f = fixture(1);
    provider(f, "ABORTED");
    queryEntered = new CountDownLatch(1);
    queryRelease = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      Future<Outcome> running = executor.submit(() -> confirmation.confirm(f.payment(), f.admin()));
      assertThat(queryEntered.await(30, TimeUnit.SECONDS)).isTrue();
      // A worker that was not fenced commits its own finalization while the command queries.
      new TransactionTemplate(manager)
          .executeWithoutResult(
              status -> {
                jdbc.queryForObject("SELECT id FROM payments WHERE id=? FOR UPDATE", Long.class, f.payment());
                jdbc.update("UPDATE payments SET status=? WHERE id=?", normalResult, f.payment());
                if (!"UNKNOWN".equals(normalResult))
                  jdbc.update("UPDATE orders SET status=? WHERE id=?",
                      "SUCCEEDED".equals(normalResult) ? "PAID" : "PAYMENT_FAILED", f.order());
              });
      var afterWorker = state(f);
      queryRelease.countDown();
      assertThat(running.get(30, TimeUnit.SECONDS)).isEqualTo(Outcome.CHANGED_DURING_QUERY);
      assertThat(state(f)).isEqualTo(afterWorker);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void f11ConfirmationWaitsForAnotherInventoryLockAndThenReleasesEverySku() throws Exception {
    Fixture f = fixture(2);
    provider(f, "EXPIRED");
    long contended = f.skus().get(1);
    var locked = new CountDownLatch(1);
    var unlock = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> holder = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
        jdbc.queryForObject("SELECT sku_id FROM inventories WHERE sku_id=? FOR UPDATE", Long.class, contended);
        locked.countDown();
        try {
          assertThat(unlock.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      }));
      assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();
      Future<Outcome> running = executor.submit(() -> confirmation.confirm(f.payment(), f.admin()));
      Thread.sleep(1500);
      assertThat(running).isNotDone();
      assertThat(one("SELECT status FROM payments WHERE id=?", f.payment())).isEqualTo("PROCESSING");
      assertThat(count("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=? AND type='RELEASE'", f.payment())).isZero();
      unlock.countDown();
      holder.get(30, TimeUnit.SECONDS);
      assertThat(running.get(30, TimeUnit.SECONDS)).isEqualTo(Outcome.FAILURE_CONFIRMED);
    } finally {
      unlock.countDown();
      executor.shutdownNow();
    }
    assertThat(count("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=? AND type='RELEASE'", f.payment())).isEqualTo(2);
    for (long sku : f.skus())
      assertThat(count("SELECT reserved_quantity FROM inventories WHERE sku_id=?", sku)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"coupon", "audit"})
  void f12FailureAtALateStepRollsBackEverythingAndRetryConfirms(String step) {
    Fixture f = fixture(2);
    provider(f, "ABORTED");
    var before = state(f);
    if ("coupon".equals(step))
      doThrow(new IllegalStateException("injected at coupon")).when(persistence).releaseReservedCoupon(anyLong());
    else
      // Stub the spy behind the @Transactional proxy: stubbing through the proxy would call the
      // spied transaction manager while matchers are pending.
      doThrow(new IllegalStateException("injected at audit"))
          .when((AdminAuditService) AopTestUtils.getUltimateTargetObject(audits))
          .append(anyLong(), anyString(), anyString(), anyLong());

    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.FAILED_ROLLED_BACK);
    assertThat(state(f)).isEqualTo(before);

    reset(persistence);
    reset((AdminAuditService) AopTestUtils.getUltimateTargetObject(audits));
    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.FAILURE_CONFIRMED);
    assertThat(one("SELECT status FROM orders WHERE id=?", f.order())).isEqualTo("PAYMENT_FAILED");
  }

  @Test
  void f13LostCommitAcknowledgementIsResolvedFromCommittedEvidence() {
    Fixture f = fixture(2);
    provider(f, "EXPIRED");
    loseCommitAckFor = f.payment();

    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.CONFIRMED_AFTER_COMMIT_ERROR);

    assertThat(one("SELECT status FROM payments WHERE id=?", f.payment())).isEqualTo("FAILED");
    assertThat(one("SELECT status FROM orders WHERE id=?", f.order())).isEqualTo("PAYMENT_FAILED");
    assertThat(count("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=? AND type='RELEASE'", f.payment())).isEqualTo(2);
    assertThat(auditRows(f.payment())).isEqualTo(1);
    var after = state(f);
    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.ALREADY_CONFIRMED);
    assertThat(state(f)).isEqualTo(after);
  }

  @Test
  void f14LostCommitAcknowledgementWithUnreadableEvidenceIsUndetermined() {
    Fixture f = fixture(1);
    provider(f, "ABORTED");
    loseCommitAckFor = f.payment();
    doThrow(new IllegalStateException("injected: database unreadable"))
        .when(persistence).findFailureEvidence(anyLong(), anyString(), any());

    assertThat(confirmation.confirm(f.payment(), f.admin())).isEqualTo(Outcome.UNDETERMINED);
  }

  private void provider(Fixture f, String remote) {
    fake.respond(200, TossPaymentQuerySafetyTests.FakeServer.payment(f.identity(), "key-1", "123.45", remote));
  }

  private Fixture fixture(int skuCount) {
    return fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING", skuCount);
  }

  /** SKU i has order quantity i+2, stock 10, and is reserved by the payment. */
  private Fixture fixture(String type, String status, String orderStatus, int skuCount) {
    return new TransactionTemplate(manager).execute(tx -> {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      long member = fixtures.member();
      long admin = fixtures.member();
      jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
      long order = fixtures.order(member, "ONE_TIME", orderStatus, new BigDecimal("123.45"), null);
      long payment = fixtures.payment(order, type, status, 1);
      String identity = "order-" + payment;
      jdbc.update(
          "UPDATE payments SET provider_order_id=?,payment_key='key-1',expires_at=? WHERE id=?",
          identity, SecondaryReadFixtures.stamp(), payment);
      List<Long> skus = new ArrayList<>();
      for (int i = 0; i < skuCount; i++) {
        var product = fixtures.product(fixtures.category(true), fixtures.brand(true), "DOG", "PUBLIC");
        var sku = fixtures.sku(product, SkuStatus.ACTIVE, 10);
        int quantity = i + 2;
        fixtures.item(order, sku);
        jdbc.update("UPDATE order_items SET quantity=? WHERE order_id=? AND sku_id=?", quantity, order, sku.getId());
        jdbc.update("UPDATE inventories SET available_quantity=?,reserved_quantity=? WHERE sku_id=?",
            10 - quantity, quantity, sku.getId());
        movement(payment, sku.getId(), "RESERVE", quantity);
        skus.add(sku.getId());
      }
      jdbc.update("INSERT INTO carts(member_id,version,created_at,updated_at) VALUES (?,7,?,?)", member, SecondaryReadFixtures.stamp(), SecondaryReadFixtures.stamp());
      long cart = fixtures.lastId();
      jdbc.update("INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?,?,5)", cart, skus.get(0));
      jdbc.update("INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,valid_from,valid_until,active) VALUES ('417 failure','FIXED_AMOUNT',1,0,'2026-01-01','2027-01-01',true)");
      long coupon = fixtures.lastId();
      jdbc.update("INSERT INTO member_coupons(member_id,coupon_id,status,reserved_order_id,issued_at) VALUES (?,?,'RESERVED',?,?)", member, coupon, order, SecondaryReadFixtures.stamp());
      long memberCoupon = fixtures.lastId();
      return new Fixture(member, admin, order, payment, List.copyOf(skus), cart, memberCoupon, identity);
    });
  }

  private void movement(long payment, long sku, String type, int quantity) {
    jdbc.update(
        "INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at) VALUES (?,?,?,?,10,?,0,?,?)",
        sku, payment, type, quantity, 10 - quantity, quantity, SecondaryReadFixtures.stamp());
  }

  private Map<String, Object> state(Fixture f) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("payments", jdbc.queryForList("SELECT * FROM payments WHERE order_id=? ORDER BY id", f.order()));
    state.put("order", jdbc.queryForList("SELECT * FROM orders WHERE id=?", f.order()));
    for (long sku : f.skus())
      state.put("inventory-" + sku, jdbc.queryForList("SELECT * FROM inventories WHERE sku_id=?", sku));
    state.put("movements", jdbc.queryForList("SELECT * FROM inventory_movements WHERE payment_id=? ORDER BY id", f.payment()));
    state.put("cart", jdbc.queryForList("SELECT * FROM carts WHERE id=?", f.cart()));
    state.put("items", jdbc.queryForList("SELECT * FROM cart_items WHERE cart_id=?", f.cart()));
    state.put("coupon", jdbc.queryForList("SELECT * FROM member_coupons WHERE id=?", f.coupon()));
    state.put("delivery", jdbc.queryForList("SELECT * FROM deliveries WHERE order_id=?", f.order()));
    state.put("notifications", jdbc.queryForList("SELECT * FROM notifications WHERE member_id=? ORDER BY id", f.member()));
    state.put("audit", jdbc.queryForList("SELECT * FROM admin_audit_logs WHERE target_id=? AND target_type='PAYMENT'", f.payment()));
    return state;
  }

  private int auditRows(long payment) {
    return count("SELECT COUNT(*) FROM admin_audit_logs WHERE target_type='PAYMENT' AND target_id=? AND action IN (?,?)",
        payment, ACTIONS.get(0), ACTIONS.get(1));
  }

  private String one(String sql, long id) {
    return jdbc.queryForObject(sql, String.class, id);
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private record Fixture(
      long member, long admin, long order, long payment, List<Long> skus, long cart, long coupon, String identity) {}
}
