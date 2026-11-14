package com.pawcycle.backend.commerce.payment.infrastructure.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.membership.application.MembershipEvaluationService;
import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentRecoveryService;
import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentRecoveryService.Outcome;
import com.pawcycle.backend.commerce.payment.application.TossPaymentAdapter;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #417 Phase 2a on disposable MySQL with the real Toss query parser behind a fake HTTP server.
 * The provider confirm endpoint must never be called by recovery.
 */
@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class ProcessingPaymentRecoveryIntegrationTests {
  @Autowired ProcessingPaymentRecoveryService recovery;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PlatformTransactionManager manager;
  @MockitoBean TossPaymentAdapter provider;
  @MockitoSpyBean MembershipEvaluationService membership;
  TossPaymentQuerySafetyTests.FakeServer fake;
  volatile CountDownLatch queryEntered;
  volatile CountDownLatch queryRelease;

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
  }

  @AfterEach
  void stop() {
    fake.close();
    verify(provider, never()).confirm(anyString(), anyString(), any());
    reset(membership);
  }

  @Test
  void t1ProviderDoneOfSamePaymentAppliesNormalSuccessEffectsOnce() {
    Fixture f = fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING");
    done(f);

    assertThat(recovery.recover(f.payment(), f.admin())).isEqualTo(Outcome.RECOVERED);

    assertThat(one("SELECT status FROM payments WHERE id=?", f.payment())).isEqualTo("SUCCEEDED");
    assertThat(one("SELECT status FROM orders WHERE id=?", f.order())).isEqualTo("PAID");
    assertThat(count("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=? AND type='DEDUCT'", f.payment())).isEqualTo(1);
    assertThat(count("SELECT reserved_quantity FROM inventories WHERE sku_id=?", f.sku())).isZero();
    assertThat(count("SELECT COUNT(*) FROM deliveries WHERE order_id=?", f.order())).isEqualTo(1);
    assertThat(one("SELECT status FROM member_coupons WHERE reserved_order_id=?", f.order())).isEqualTo("USED");
    assertThat(count("SELECT quantity FROM cart_items WHERE cart_id=?", f.cart())).isEqualTo(3);
    assertThat(count("SELECT COUNT(*) FROM notifications WHERE member_id=? AND type='ORDER_PAID'", f.member())).isEqualTo(1);
    assertThat(count("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND action='PAYMENT_PROCESSING_RECOVERED' AND target_id=?", f.admin(), f.payment())).isEqualTo(1);
    verify(membership).evaluate(f.member());
    assertThat(fake.requests.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 500})
  void t2ProviderHttpErrorsStopWithoutChangingAnything(int status) {
    Fixture f = fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING");
    fake.respond(status, "{\"code\":\"QUERY_ERROR\"}");
    var before = state(f);
    assertThat(recovery.recover(f.payment(), f.admin())).isEqualTo(Outcome.STOPPED_PROVIDER_UNKNOWN);
    assertThat(state(f)).isEqualTo(before);
  }

  @ParameterizedTest
  @CsvSource({
    "IN_PROGRESS,STOPPED_PROVIDER_UNKNOWN",
    "CANCELED,STOPPED_PROVIDER_UNKNOWN",
    "PARTIAL_CANCELED,STOPPED_PROVIDER_UNKNOWN",
    "ABORTED,STOPPED_PROVIDER_FAILED",
    "EXPIRED,STOPPED_PROVIDER_FAILED"
  })
  void t3NonFinalOrFailedProviderStatesStopWithoutReleasingReservation(String remote, Outcome expected) {
    Fixture f = fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING");
    fake.respond(200, TossPaymentQuerySafetyTests.FakeServer.payment(f.identity(), "key-1", "123.45", remote));
    var before = state(f);
    assertThat(recovery.recover(f.payment(), f.admin())).isEqualTo(expected);
    assertThat(state(f)).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"order", "key", "amount"})
  void t4IdentityMismatchStops(String mismatch) {
    Fixture f = fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING");
    fake.respond(
        200,
        TossPaymentQuerySafetyTests.FakeServer.payment(
            mismatch.equals("order") ? "other-order" : f.identity(),
            mismatch.equals("key") ? "other-key" : "key-1",
            mismatch.equals("amount") ? "999.99" : "123.45",
            "DONE"));
    var before = state(f);
    assertThat(recovery.recover(f.payment(), f.admin())).isEqualTo(Outcome.STOPPED_PROVIDER_UNKNOWN);
    assertThat(state(f)).isEqualTo(before);
  }

  @Test
  void t5RerunAfterRecoveryIsRefusedWithoutDuplicateEffects() {
    Fixture f = fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING");
    done(f);
    assertThat(recovery.recover(f.payment(), f.admin())).isEqualTo(Outcome.RECOVERED);
    var after = state(f);
    assertThat(recovery.recover(f.payment(), f.admin())).isEqualTo(Outcome.REFUSED_NOT_PROCESSING);
    assertThat(state(f)).isEqualTo(after);
  }

  @Test
  void t6LateNormalFinalizationDuringProviderQueryWinsAndRecoveryAppliesNothing() throws Exception {
    Fixture f = fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING");
    done(f);
    queryEntered = new CountDownLatch(1);
    queryRelease = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      Future<Outcome> running = executor.submit(() -> recovery.recover(f.payment(), f.admin()));
      assertThat(queryEntered.await(30, TimeUnit.SECONDS)).isTrue();
      // The original confirm's TX2 commits while recovery is outside any transaction.
      new TransactionTemplate(manager)
          .executeWithoutResult(
              status -> {
                jdbc.queryForObject("SELECT id FROM payments WHERE id=? FOR UPDATE", Long.class, f.payment());
                jdbc.update("UPDATE payments SET status='SUCCEEDED',provider_status='DONE' WHERE id=?", f.payment());
                jdbc.update("UPDATE orders SET status='PAID' WHERE id=?", f.order());
              });
      var afterNormal = state(f);
      queryRelease.countDown();
      assertThat(running.get(30, TimeUnit.SECONDS)).isEqualTo(Outcome.ALREADY_FINALIZED);
      assertThat(state(f)).isEqualTo(afterNormal);
    } finally {
      queryEntered = null;
      executor.shutdownNow();
    }
  }

  @Test
  void t7TwoConcurrentRecoveriesApplyEffectsOnce() throws Exception {
    Fixture f = fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING");
    done(f);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<Outcome>> runs = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        runs.add(executor.submit(() -> {
          start.await();
          return recovery.recover(f.payment(), f.admin());
        }));
      start.countDown();
      List<Outcome> outcomes = new ArrayList<>();
      for (var run : runs) outcomes.add(run.get(60, TimeUnit.SECONDS));
      assertThat(outcomes).containsOnlyOnce(Outcome.RECOVERED);
      assertThat(outcomes).allMatch(o -> o == Outcome.RECOVERED || o == Outcome.ALREADY_FINALIZED || o == Outcome.REFUSED_NOT_PROCESSING);
    } finally {
      executor.shutdownNow();
    }
    assertThat(count("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=? AND type='DEDUCT'", f.payment())).isEqualTo(1);
    assertThat(count("SELECT COUNT(*) FROM deliveries WHERE order_id=?", f.order())).isEqualTo(1);
    assertThat(count("SELECT COUNT(*) FROM admin_audit_logs WHERE action='PAYMENT_PROCESSING_RECOVERED' AND target_id=?", f.payment())).isEqualTo(1);
  }

  @Test
  void t8FailureInsideFinalizationRollsBackEverythingAndRetrySucceeds() {
    Fixture f = fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING");
    done(f);
    var before = state(f);
    doThrow(new IllegalStateException("injected after earlier effects")).when(membership).evaluate(anyLong());
    assertThatThrownBy(() -> recovery.recover(f.payment(), f.admin())).isInstanceOf(IllegalStateException.class);
    assertThat(state(f)).isEqualTo(before);
    assertThat(count("SELECT COUNT(*) FROM admin_audit_logs WHERE target_id=? AND action='PAYMENT_PROCESSING_RECOVERED'", f.payment())).isZero();

    reset(membership);
    assertThat(recovery.recover(f.payment(), f.admin())).isEqualTo(Outcome.RECOVERED);
    assertThat(one("SELECT status FROM payments WHERE id=?", f.payment())).isEqualTo("SUCCEEDED");
  }

  @ParameterizedTest
  @CsvSource({
    "BILLING,PROCESSING,PAYMENT_PENDING,,REFUSED_NOT_NORMAL",
    "NORMAL,READY,PAYMENT_PENDING,,REFUSED_NOT_PROCESSING",
    "NORMAL,UNKNOWN,PAYMENT_PENDING,,REFUSED_NOT_PROCESSING",
    "NORMAL,PROCESSING,PAID,,REFUSED_ORDER_NOT_PENDING",
    "NORMAL,PROCESSING,PAYMENT_PENDING,no-key,REFUSED_IDENTITY_MISSING",
    "NORMAL,PROCESSING,PAYMENT_PENDING,recent,REFUSED_TOO_RECENT",
    "NORMAL,PROCESSING,PAYMENT_PENDING,no-expiry,REFUSED_TOO_RECENT",
    "NORMAL,PROCESSING,PAYMENT_PENDING,no-reserve,REFUSED_RESERVATION_MISMATCH",
    "NORMAL,PROCESSING,PAYMENT_PENDING,settled,REFUSED_RESERVATION_MISMATCH"
  })
  void t9IneligiblePaymentsAreRefusedBeforeProviderIo(
      String type, String status, String orderStatus, String variant, Outcome expected) {
    Fixture f = fixture(type, status, orderStatus);
    if ("no-key".equals(variant)) jdbc.update("UPDATE payments SET payment_key=NULL WHERE id=?", f.payment());
    if ("recent".equals(variant)) jdbc.update("UPDATE payments SET expires_at=? WHERE id=?", Timestamp.from(Instant.now()), f.payment());
    if ("no-expiry".equals(variant)) jdbc.update("UPDATE payments SET expires_at=NULL WHERE id=?", f.payment());
    if ("no-reserve".equals(variant)) jdbc.update("DELETE FROM inventory_movements WHERE payment_id=?", f.payment());
    if ("settled".equals(variant)) movement(f, "RELEASE");
    done(f);
    var before = state(f);
    assertThat(recovery.recover(f.payment(), f.admin())).isEqualTo(expected);
    assertThat(state(f)).isEqualTo(before);
    assertThat(fake.requests.get()).isZero();
  }

  @Test
  void t10NonAdminOperatorIsRefusedBeforeAnyRead() {
    Fixture f = fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING");
    done(f);
    var before = state(f);
    assertThat(recovery.recover(f.payment(), f.member())).isEqualTo(Outcome.REFUSED_OPERATOR_NOT_ADMIN);
    assertThat(state(f)).isEqualTo(before);
    assertThat(fake.requests.get()).isZero();
  }

  @Test
  void unconfiguredProviderIsRefused() {
    Fixture f = fixture("NORMAL", "PROCESSING", "PAYMENT_PENDING");
    when(provider.isConfigured()).thenReturn(false);
    var before = state(f);
    assertThat(recovery.recover(f.payment(), f.admin())).isEqualTo(Outcome.REFUSED_PROVIDER_UNAVAILABLE);
    assertThat(state(f)).isEqualTo(before);
  }

  private void done(Fixture f) {
    fake.respond(200, TossPaymentQuerySafetyTests.FakeServer.payment(f.identity(), "key-1", "123.45", "DONE"));
  }

  private Fixture fixture(String type, String status, String orderStatus) {
    return new TransactionTemplate(manager).execute(tx -> {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      long member = fixtures.member();
      long admin = fixtures.member();
      jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
      var product = fixtures.product(fixtures.category(true), fixtures.brand(true), "DOG", "PUBLIC");
      var sku = fixtures.sku(product, SkuStatus.ACTIVE, 10);
      long order = fixtures.order(member, "ONE_TIME", orderStatus, new BigDecimal("123.45"), null);
      fixtures.item(order, sku);
      jdbc.update("UPDATE order_items SET quantity=2 WHERE order_id=?", order);
      jdbc.update("UPDATE inventories SET available_quantity=8,reserved_quantity=2 WHERE sku_id=?", sku.getId());
      long payment = fixtures.payment(order, type, status, 1);
      String identity = "order-" + payment;
      jdbc.update(
          "UPDATE payments SET provider_order_id=?,payment_key='key-1',expires_at=? WHERE id=?",
          identity, SecondaryReadFixtures.stamp(), payment);
      Fixture f = new Fixture(member, admin, order, payment, sku.getId(), 0, identity);
      movement(f, "RESERVE");
      jdbc.update("INSERT INTO carts(member_id,version,created_at,updated_at) VALUES (?,7,?,?)", member, SecondaryReadFixtures.stamp(), SecondaryReadFixtures.stamp());
      long cart = fixtures.lastId();
      jdbc.update("INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?,?,5)", cart, sku.getId());
      jdbc.update("INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,valid_from,valid_until,active) VALUES ('F07 recovery','FIXED_AMOUNT',1,0,'2026-01-01','2027-01-01',true)");
      long coupon = fixtures.lastId();
      jdbc.update("INSERT INTO member_coupons(member_id,coupon_id,status,reserved_order_id,issued_at) VALUES (?,?,'RESERVED',?,?)", member, coupon, order, SecondaryReadFixtures.stamp());
      return new Fixture(member, admin, order, payment, sku.getId(), cart, identity);
    });
  }

  private void movement(Fixture f, String type) {
    jdbc.update(
        "INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at) VALUES (?,?,?,2,10,8,0,2,?)",
        f.sku(), f.payment(), type, SecondaryReadFixtures.stamp());
  }

  private Map<String, Object> state(Fixture f) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("payment", jdbc.queryForList("SELECT * FROM payments WHERE id=?", f.payment()));
    state.put("order", jdbc.queryForList("SELECT * FROM orders WHERE id=?", f.order()));
    state.put("inventory", jdbc.queryForList("SELECT * FROM inventories WHERE sku_id=?", f.sku()));
    state.put("movements", jdbc.queryForList("SELECT * FROM inventory_movements WHERE payment_id=? ORDER BY id", f.payment()));
    state.put("items", jdbc.queryForList("SELECT * FROM cart_items WHERE cart_id=?", f.cart()));
    state.put("coupon", jdbc.queryForList("SELECT * FROM member_coupons WHERE reserved_order_id=?", f.order()));
    state.put("delivery", jdbc.queryForList("SELECT * FROM deliveries WHERE order_id=?", f.order()));
    state.put("notifications", jdbc.queryForList("SELECT * FROM notifications WHERE member_id=? ORDER BY id", f.member()));
    state.put("audit", jdbc.queryForList("SELECT * FROM admin_audit_logs WHERE target_id=? AND action='PAYMENT_PROCESSING_RECOVERED'", f.payment()));
    return state;
  }

  private String one(String sql, long id) {
    return jdbc.queryForObject(sql, String.class, id);
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private record Fixture(long member, long admin, long order, long payment, long sku, long cart, String identity) {}
}
