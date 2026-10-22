package com.pawcycle.backend.commerce.payment.infrastructure.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.payment.application.PaymentReconciliationService;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class PaymentProviderQuerySafetyIntegrationTests {
  @Autowired PaymentReconciliationService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PlatformTransactionManager manager;
  @MockitoBean TossPaymentAdapter provider;
  TossPaymentQuerySafetyTests.FakeServer fake;

  @BeforeEach void start() throws Exception {
    fake = new TossPaymentQuerySafetyTests.FakeServer();
    TossPaymentAdapter real = fake.adapter();
    when(provider.isConfigured()).thenReturn(true);
    when(provider.queryPayment(anyString(), any(), any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return real.queryPayment(call.getArgument(0), call.getArgument(1), call.getArgument(2));
    });
  }
  @AfterEach void stop() {
    fake.close();
    verify(provider, never()).confirm(anyString(), anyString(), any());
  }

  @ParameterizedTest
  @CsvSource({"PROCESSING,DONE,SUCCEEDED", "PROCESSING,ABORTED,FAILED", "UNKNOWN,DONE,SUCCEEDED",
      "UNKNOWN,IN_PROGRESS,UNKNOWN", "PROCESSING,CANCELED,UNKNOWN", "PROCESSING,HTTP_404,UNKNOWN"})
  void observationDoesNotMutateAnyLocalAggregateEvenWithAnAmbientTransaction(
      String localStatus, String remoteStatus, String classification) {
    Fixture f = fixture(localStatus);
    fake.respond(remoteStatus.equals("HTTP_404") ? 404 : 200, TossPaymentQuerySafetyTests.FakeServer.payment(f.orderIdentity(), "key-1", "123.45", remoteStatus));
    var before = state(f);
    new TransactionTemplate(manager).executeWithoutResult(tx -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(service.queryOnly(f.payment()).status()).isEqualTo(classification);
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    });
    assertThat(state(f)).isEqualTo(before);
    assertThat(fake.lastMethod).isEqualTo("GET");
    assertThat(fake.requests.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 500})
  void existingUnknownReconciliationKeepsReservationsOnQueryHttpErrors(int status) {
    Fixture f = fixture("UNKNOWN");
    fake.respond(status, "{\"code\":\"QUERY_ERROR\"}");
    var before = state(f);
    assertThat(service.reconcile(f.payment()).status()).isEqualTo("UNKNOWN");
    assertBusinessStateUnchanged(f, before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"identity", "amount", "key", "pending", "canceled"})
  void existingUnknownReconciliationCannotFinalizeAmbiguousPayment(String scenario) {
    Fixture f = fixture("UNKNOWN");
    fake.respond(200, TossPaymentQuerySafetyTests.FakeServer.payment(
        scenario.equals("identity") ? "wrong-order" : f.orderIdentity(),
        scenario.equals("key") ? "wrong-key" : "key-1",
        scenario.equals("amount") ? "124.45" : "123.45",
        scenario.equals("pending") ? "IN_PROGRESS" : scenario.equals("canceled") ? "CANCELED" : "DONE"));
    var before = state(f);
    assertThat(service.reconcile(f.payment()).status()).isEqualTo("UNKNOWN");
    assertBusinessStateUnchanged(f, before);
  }

  @ParameterizedTest
  @CsvSource({"DONE,SUCCEEDED,PAID", "ABORTED,FAILED,PAYMENT_FAILED", "EXPIRED,FAILED,PAYMENT_FAILED"})
  void matchingFinalProviderStateKeepsExistingReconciliationEffects(
      String remoteStatus, String paymentStatus, String orderStatus) {
    Fixture f = fixture("UNKNOWN");
    fake.respond(200, TossPaymentQuerySafetyTests.FakeServer.payment(f.orderIdentity(), "key-1", "123.45", remoteStatus));
    assertThat(service.reconcile(f.payment()).status()).isEqualTo(paymentStatus);
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, f.order())).isEqualTo(orderStatus);
    assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM inventories WHERE sku_id=?", Integer.class, f.sku())).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=?", Integer.class, f.payment())).isEqualTo(1);
    boolean success = "SUCCEEDED".equals(paymentStatus);
    assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE id=?", Long.class, f.cart())).isEqualTo(success ? 8L : 7L);
    assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE cart_id=?", Integer.class, f.cart())).isEqualTo(success ? 3 : 5);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deliveries WHERE order_id=?", Integer.class, f.order())).isEqualTo(success ? 1 : 0);
    assertThat(jdbc.queryForObject("SELECT status FROM member_coupons WHERE member_id=?", String.class, f.member())).isEqualTo(success ? "USED" : "AVAILABLE");
    assertThat(fake.lastMethod).isEqualTo("GET");
    assertThat(fake.requests.get()).isEqualTo(1);
  }

  @Test void missingStoredKeyNeverQueriesOrMutatesThePayment() {
    Fixture f = fixture("PROCESSING");
    jdbc.update("UPDATE payments SET payment_key=NULL WHERE id=?", f.payment());
    var before = state(f);
    assertThat(service.queryOnly(f.payment()).status()).isEqualTo("UNKNOWN");
    assertThat(state(f)).isEqualTo(before);
    assertThat(fake.requests.get()).isZero();
  }

  @Test void normalProcessingReconciliationGateStaysClosed() {
    Fixture f = fixture("PROCESSING");
    var before = state(f);
    assertThatThrownBy(() -> service.reconcile(f.payment()))
        .isInstanceOfSatisfying(CommerceException.class,
            e -> assertThat(e.code()).isEqualTo("PAYMENT_RECONCILIATION_NOT_ALLOWED"));
    assertThat(state(f)).isEqualTo(before);
    assertThat(fake.requests.get()).isZero();
  }

  private void assertBusinessStateUnchanged(Fixture f, Map<String, Object> before) {
    var after = state(f);
    before.remove("observation"); after.remove("observation");
    assertThat(after).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, f.payment())).isEqualTo("UNKNOWN");
    assertThat(jdbc.queryForObject("SELECT reconciliation_attempts FROM payments WHERE id=?", Integer.class, f.payment())).isEqualTo(1);
    assertThat(fake.lastMethod).isEqualTo("GET");
  }

  private Fixture fixture(String status) {
    return new TransactionTemplate(manager).execute(tx -> {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      long member = fixtures.member();
      var product = fixtures.product(fixtures.category(true), fixtures.brand(true), "DOG", "PUBLIC");
      var sku = fixtures.sku(product, SkuStatus.ACTIVE, 10);
      long order = fixtures.order(member, "ONE_TIME", "PAYMENT_PENDING", new BigDecimal("123.45"), null);
      fixtures.item(order, sku);
      jdbc.update("UPDATE order_items SET quantity=2 WHERE order_id=?", order);
      jdbc.update("UPDATE inventories SET reserved_quantity=2 WHERE sku_id=?", sku.getId());
      long payment = fixtures.payment(order, "NORMAL", status, 1);
      String identity = "order-" + payment;
      jdbc.update("UPDATE payments SET provider_order_id=?,payment_key='key-1' WHERE id=?", identity, payment);
      jdbc.update("INSERT INTO carts(member_id,version,created_at,updated_at) VALUES (?,7,?,?)", member, SecondaryReadFixtures.stamp(), SecondaryReadFixtures.stamp());
      long cart = fixtures.lastId();
      jdbc.update("INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?,?,5)", cart, sku.getId());
      jdbc.update("INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,valid_from,valid_until,active) VALUES ('F07 query','FIXED_AMOUNT',1,0,'2026-01-01','2027-01-01',true)");
      long coupon = fixtures.lastId();
      jdbc.update("INSERT INTO member_coupons(member_id,coupon_id,status,reserved_order_id,issued_at) VALUES (?,?,'RESERVED',?,?)", member, coupon, order, SecondaryReadFixtures.stamp());
      return new Fixture(member, order, payment, sku.getId(), cart, identity);
    });
  }

  private Map<String, Object> state(Fixture f) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("payment", jdbc.queryForList("SELECT id,order_id,type,provider,status,provider_status,amount,provider_order_id,payment_key,idempotency_key,attempt_no,failure_code,failure_message,requested_at,approved_at,failed_at,expires_at,created_at FROM payments WHERE id=?", f.payment()));
    state.put("observation", jdbc.queryForList("SELECT reconciliation_attempts,last_reconciled_at FROM payments WHERE id=?", f.payment()));
    state.put("order", jdbc.queryForList("SELECT * FROM orders WHERE id=?", f.order()));
    state.put("inventory", jdbc.queryForList("SELECT * FROM inventories WHERE sku_id=?", f.sku()));
    state.put("movements", jdbc.queryForList("SELECT * FROM inventory_movements WHERE payment_id=? ORDER BY id", f.payment()));
    state.put("cart", jdbc.queryForList("SELECT * FROM carts WHERE id=?", f.cart()));
    state.put("items", jdbc.queryForList("SELECT * FROM cart_items WHERE cart_id=?", f.cart()));
    state.put("coupon", jdbc.queryForList("SELECT * FROM member_coupons WHERE reserved_order_id=?", f.order()));
    state.put("delivery", jdbc.queryForList("SELECT * FROM deliveries WHERE order_id=?", f.order()));
    state.put("notifications", jdbc.queryForList("SELECT * FROM notifications WHERE member_id=? ORDER BY id", f.member()));
    return state;
  }
  private record Fixture(long member, long order, long payment, long sku, long cart, String orderIdentity) {}
}