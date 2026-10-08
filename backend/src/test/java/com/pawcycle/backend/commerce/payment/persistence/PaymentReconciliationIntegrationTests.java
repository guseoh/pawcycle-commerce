package com.pawcycle.backend.commerce.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.payment.application.PaymentReconciliationService;
import com.pawcycle.backend.commerce.payment.infrastructure.toss.TossPaymentAdapter;
import com.pawcycle.backend.commerce.billing.infrastructure.toss.TossBillingAdapter;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.notification.application.NotificationService;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

/** Real MySQL transactions; only external provider I/O is replaced. No outer test transaction. */
@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class PaymentReconciliationIntegrationTests {
  @Autowired private PaymentReconciliationService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private PlatformTransactionManager manager;
  @MockitoBean private TossPaymentAdapter provider;
  @MockitoBean private TossBillingAdapter billingProvider;
  @MockitoSpyBean private NotificationService notifications;

  @Test
  void successConsumesExistingCartQuantityAfterProviderQueryOutsideTransaction() {
    Fixture f = fixture(5);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.queryPayment(anyString())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      assertThat(integer("SELECT reconciliation_attempts FROM payments WHERE id=?", f.payment())).isEqualTo(1);
      return new TossPaymentAdapter.ConfirmResult("SUCCEEDED", "DONE");
    });

    assertThat(service.reconcile(f.payment()).status()).isEqualTo("SUCCEEDED");
    assertThat(integer("SELECT quantity FROM cart_items WHERE cart_id=? AND sku_id=?", f.cart(), f.sku())).isEqualTo(3);
    assertThat(integer("SELECT reserved_quantity FROM inventories WHERE sku_id=?", f.sku())).isZero();
    assertThat(integer("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=? AND type='DEDUCT'", f.payment())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, f.order())).isEqualTo("PAID");
    assertThat(jdbc.queryForObject("SELECT provider_status FROM payments WHERE id=?", String.class, f.payment())).isEqualTo("DONE");
    assertThat(integer("SELECT COUNT(*) FROM deliveries WHERE order_id=? AND status='PREPARING'", f.order())).isEqualTo(1);
    assertThat(integer("SELECT COUNT(*) FROM member_memberships WHERE member_id=?", f.member())).isEqualTo(1);
    assertThat(integer("SELECT COUNT(*) FROM notifications WHERE member_id=? AND type='ORDER_PAID' AND reference_id=?", f.member(), f.order())).isEqualTo(1);
    assertThat(integer("SELECT version FROM carts WHERE id=?", f.cart())).isEqualTo(7);
    assertThat(jdbc.queryForObject("SELECT status FROM member_coupons WHERE id=?", String.class, f.coupon())).isEqualTo("USED");
    assertThat(jdbc.queryForObject("SELECT updated_at FROM carts WHERE id=?", java.sql.Timestamp.class, f.cart())).isAfter(SecondaryReadFixtures.stamp());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void successDeletesCartItemWhenQuantityIsLessThanOrEqualToOrder(int quantity) {
    Fixture f = fixture(quantity);
    succeed();
    assertThat(service.reconcile(f.payment()).status()).isEqualTo("SUCCEEDED");
    assertThat(integer("SELECT COUNT(*) FROM cart_items WHERE cart_id=?", f.cart())).isZero();
    assertThat(integer("SELECT version FROM carts WHERE id=?", f.cart())).isEqualTo(7);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void successToleratesAbsentCartItemOrCartWithoutCreatingOne(boolean noCart) {
    Fixture f = fixture(null);
    if (noCart) jdbc.update("DELETE FROM carts WHERE id=?", f.cart());
    succeed();
    assertThat(service.reconcile(f.payment()).status()).isEqualTo("SUCCEEDED");
    assertThat(integer("SELECT COUNT(*) FROM carts WHERE member_id=?", f.member())).isEqualTo(noCart ? 0 : 1);
    assertThat(integer("SELECT COUNT(*) FROM cart_items WHERE cart_id=?", f.cart())).isZero();
  }

  @ParameterizedTest
  @CsvSource({"NORMAL,READY", "NORMAL,PROCESSING", "NORMAL,SUCCEEDED", "NORMAL,FAILED", "BILLING,READY", "BILLING,SUCCEEDED", "BILLING,FAILED"})
  void rejectsStatusesOutsideRecoveryGateBeforeProviderIo(String type, String status) {
    Fixture f = fixture(5);
    jdbc.update("UPDATE payments SET type=?,status=? WHERE id=?", type, status, f.payment());
    assertError(f, 409, "PAYMENT_RECONCILIATION_NOT_ALLOWED");
    assertThat(integer("SELECT reconciliation_attempts FROM payments WHERE id=?", f.payment())).isZero();
    verifyNoInteractions(provider, billingProvider);
  }

  @Test
  void providerUnavailableAndAttemptCapLeaveAllStateUnchanged() {
    Fixture f = fixture(5);
    assertError(f, 503, "PAYMENT_PROVIDER_UNAVAILABLE");
    assertThat(integer("SELECT reconciliation_attempts FROM payments WHERE id=?", f.payment())).isZero();
    when(provider.isConfigured()).thenReturn(true);
    jdbc.update("UPDATE payments SET reconciliation_attempts=10 WHERE id=?", f.payment());
    assertError(f, 409, "PAYMENT_RECONCILIATION_EXHAUSTED");
    assertThat(quantity(f)).isEqualTo(5);
    assertThat(integer("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=?", f.payment())).isZero();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unknownOrTimeoutAtAttemptTenRequiresActionWithoutConsumingState(boolean timeout) {
    Fixture f = fixture(5);
    jdbc.update("UPDATE payments SET reconciliation_attempts=9 WHERE id=?", f.payment());
    jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", f.member());
    when(provider.isConfigured()).thenReturn(true);
    if (timeout) when(provider.queryPayment(anyString())).thenThrow(new IllegalStateException("fixture timeout"));
    else when(provider.queryPayment(anyString())).thenReturn(new TossPaymentAdapter.ConfirmResult("UNKNOWN", "IN_PROGRESS"));
    assertThat(service.reconcile(f.payment(), f.member()).status()).isEqualTo("UNKNOWN");
    assertThat(integer("SELECT reconciliation_attempts FROM payments WHERE id=?", f.payment())).isEqualTo(10);
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, f.order())).isEqualTo("PAYMENT_ACTION_REQUIRED");
    assertThat(quantity(f)).isEqualTo(5);
    assertThat(integer("SELECT reserved_quantity FROM inventories WHERE sku_id=?", f.sku())).isEqualTo(2);
    assertThat(integer("SELECT COUNT(*) FROM notifications WHERE member_id=? AND type='PAYMENT_ACTION_REQUIRED' AND reference_id=?", f.member(), f.payment())).isEqualTo(1);
    assertThat(integer("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND target_id=? AND action='PAYMENT_RECONCILE'", f.member(), f.payment())).isEqualTo(1);
    assertThat(integer("SELECT COUNT(*) FROM deliveries WHERE order_id=?", f.order())).isZero();
  }

  @Test
  void normalFailureReleasesReservationAndCouponAndPreservesProviderFailureStatus() {
    Fixture f = fixture(5);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.queryPayment(anyString())).thenReturn(new TossPaymentAdapter.ConfirmResult("FAILED", "DECLINED_DYNAMIC"));
    assertThat(service.reconcile(f.payment()).status()).isEqualTo("FAILED");
    assertThat(jdbc.queryForObject("SELECT failure_code FROM payments WHERE id=?", String.class, f.payment())).isEqualTo("RECONCILED_FAILED");
    assertThat(jdbc.queryForObject("SELECT provider_status FROM payments WHERE id=?", String.class, f.payment())).isEqualTo("DECLINED_DYNAMIC");
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, f.order())).isEqualTo("PAYMENT_FAILED");
    assertThat(integer("SELECT available_quantity FROM inventories WHERE sku_id=?", f.sku())).isEqualTo(12);
    assertThat(integer("SELECT reserved_quantity FROM inventories WHERE sku_id=?", f.sku())).isZero();
    assertThat(integer("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=? AND type='RELEASE'", f.payment())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT status FROM member_coupons WHERE id=?", String.class, f.coupon())).isEqualTo("AVAILABLE");
    assertThat(jdbc.queryForObject("SELECT reserved_order_id FROM member_coupons WHERE id=?", Long.class, f.coupon())).isNull();
    assertThat(quantity(f)).isEqualTo(5);
    assertThat(integer("SELECT COUNT(*) FROM deliveries WHERE order_id=?", f.order())).isZero();
    assertThat(integer("SELECT COUNT(*) FROM member_memberships WHERE member_id=?", f.member())).isZero();
  }

  @Test
  void processingBillingSuccessQueriesBillingOutsideTransactionAndKeepsOneTimeCart() {
    Fixture f = fixture(5);
    jdbc.update("UPDATE payments SET type='BILLING',status='PROCESSING' WHERE id=?", f.payment());
    jdbc.update("UPDATE orders SET source='SUBSCRIPTION' WHERE id=?", f.order());
    when(billingProvider.isConfigured()).thenReturn(true);
    when(billingProvider.queryCharge(anyString())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return new TossBillingAdapter.ChargeResult("SUCCEEDED", "BILLING_DONE");
    });
    assertThat(service.reconcile(f.payment()).status()).isEqualTo("SUCCEEDED");
    assertThat(quantity(f)).isEqualTo(5);
    assertThat(jdbc.queryForObject("SELECT provider_status FROM payments WHERE id=?", String.class, f.payment())).isEqualTo("BILLING_DONE");
    verifyNoInteractions(provider);
  }

  @Test
  void failureAfterCartMutationRollsBackEntireCompletionButKeepsCommittedStartAttempt() {
    Fixture f = fixture(5);
    succeed();
    doThrow(new IllegalStateException("after cart mutation"))
        .when(notifications).create(f.member(), "ORDER_PAID", "ORDER", f.order());
    assertThatThrownBy(() -> service.reconcile(f.payment())).isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, f.payment())).isEqualTo("UNKNOWN");
    assertThat(integer("SELECT reconciliation_attempts FROM payments WHERE id=?", f.payment())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, f.order())).isEqualTo("PAYMENT_PENDING");
    assertThat(quantity(f)).isEqualTo(5);
    assertThat(integer("SELECT version FROM carts WHERE id=?", f.cart())).isEqualTo(7);
    assertThat(jdbc.queryForObject("SELECT updated_at FROM carts WHERE id=?", java.sql.Timestamp.class, f.cart())).isEqualTo(SecondaryReadFixtures.stamp());
    assertThat(integer("SELECT reserved_quantity FROM inventories WHERE sku_id=?", f.sku())).isEqualTo(2);
    assertThat(integer("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=?", f.payment())).isZero();
    assertThat(integer("SELECT COUNT(*) FROM deliveries WHERE order_id=?", f.order())).isZero();
    assertThat(integer("SELECT COUNT(*) FROM member_memberships WHERE member_id=?", f.member())).isZero();
    assertThat(jdbc.queryForObject("SELECT status FROM member_coupons WHERE id=?", String.class, f.coupon())).isEqualTo("RESERVED");
  }

  @Test
  void overlappingProviderCallbacksCompleteOnceWithoutDuplicateEffects() throws Exception {
    Fixture f = fixture(5);
    CountDownLatch queried = new CountDownLatch(2);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.queryPayment(anyString())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      queried.countDown();
      assertThat(queried.await(10, TimeUnit.SECONDS)).isTrue();
      return new TossPaymentAdapter.ConfirmResult("SUCCEEDED", "DONE");
    });
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.reconcile(f.payment()));
      var second = executor.submit(() -> service.reconcile(f.payment()));
      assertThat(first.get(15, TimeUnit.SECONDS).status()).isEqualTo("SUCCEEDED");
      assertThat(second.get(15, TimeUnit.SECONDS).status()).isEqualTo("SUCCEEDED");
    }
    assertThat(quantity(f)).isEqualTo(3);
    assertThat(integer("SELECT reconciliation_attempts FROM payments WHERE id=?", f.payment())).isEqualTo(2);
    assertThat(integer("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=?", f.payment())).isEqualTo(1);
    assertThat(integer("SELECT COUNT(*) FROM deliveries WHERE order_id=?", f.order())).isEqualTo(1);
    assertThat(integer("SELECT COUNT(*) FROM notifications WHERE member_id=? AND reference_id=? AND type='ORDER_PAID'", f.member(), f.order())).isEqualTo(1);
    assertError(f, 409, "PAYMENT_RECONCILIATION_NOT_ALLOWED");
  }

  private void succeed() {
    when(provider.isConfigured()).thenReturn(true);
    when(provider.queryPayment(anyString())).thenReturn(new TossPaymentAdapter.ConfirmResult("SUCCEEDED", "DONE"));
  }

  private int quantity(Fixture f) { return integer("SELECT quantity FROM cart_items WHERE cart_id=? AND sku_id=?", f.cart(), f.sku()); }

  private void assertError(Fixture f, int status, String code) {
    assertThatThrownBy(() -> service.reconcile(f.payment())).isInstanceOfSatisfying(CommerceException.class, e -> {
      assertThat(e.status()).isEqualTo(status);
      assertThat(e.code()).isEqualTo(code);
    });
  }

  private Fixture fixture(Integer cartQuantity) {
    return new TransactionTemplate(manager).execute(status -> {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      long member = fixtures.member();
      var category = fixtures.category(false);
      var product = fixtures.product(category, fixtures.brand(true), "DOG", "PUBLIC");
      var sku = fixtures.sku(product, SkuStatus.ACTIVE, 10);
      long order = fixtures.order(member, "ONE_TIME", "PAYMENT_PENDING", new BigDecimal("123.45"), null);
      fixtures.item(order, sku);
      jdbc.update("UPDATE order_items SET quantity=2 WHERE order_id=?", order);
      jdbc.update("UPDATE inventories SET reserved_quantity=2 WHERE sku_id=?", sku.getId());
      long payment = fixtures.payment(order, "NORMAL", "UNKNOWN", 1);
      jdbc.update("INSERT INTO carts(member_id,version,created_at,updated_at) VALUES (?,7,?,?)", member, SecondaryReadFixtures.stamp(), SecondaryReadFixtures.stamp());
      long cart = fixtures.lastId();
      if (cartQuantity != null) jdbc.update("INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?,?,?)", cart, sku.getId(), cartQuantity);
      jdbc.update("INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,valid_from,valid_until,active) VALUES ('T06 recovery','FIXED_AMOUNT',1,0,'2026-01-01','2027-01-01',true)");
      long coupon = fixtures.lastId();
      jdbc.update("INSERT INTO member_coupons(member_id,coupon_id,status,reserved_order_id,issued_at) VALUES (?,?,'RESERVED',?,?)", member, coupon, order, SecondaryReadFixtures.stamp());
      return new Fixture(member, order, payment, sku.getId(), cart, fixtures.lastId());
    });
  }

  private int integer(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
  private record Fixture(long member, long order, long payment, long sku, long cart, long coupon) {}
}
