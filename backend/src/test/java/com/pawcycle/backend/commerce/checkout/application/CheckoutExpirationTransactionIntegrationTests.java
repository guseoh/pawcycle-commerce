package com.pawcycle.backend.commerce.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pawcycle.backend.commerce.checkout.persistence.CheckoutExpirationPersistenceAdapter;
import com.pawcycle.backend.support.AfterSalesFixtures;
import com.pawcycle.backend.support.AfterSalesFixtures.OrderFixture;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CheckoutExpirationTransactionIntegrationTests {
  @Autowired private CheckoutExpirationService service;
  @Autowired private CheckoutExpirationProcessor processor;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private PlatformTransactionManager manager;
  @MockitoSpyBean private CheckoutExpirationPersistenceAdapter rows;
  private OrderFixture order;

  @BeforeEach
  void fixture() {
    order = new AfterSalesFixtures(jdbc, entities, manager).paidOrder("PREPARING", new BigDecimal("123.45"));
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      jdbc.update("UPDATE orders SET status='PAYMENT_PENDING',paid_at=NULL WHERE id=?", order.order());
      jdbc.update("UPDATE payments SET status='READY',expires_at='2000-01-01' WHERE id=?", order.payment());
      jdbc.update("UPDATE member_coupons SET status='RESERVED',used_at=NULL WHERE id=?", order.coupon());
      jdbc.update("UPDATE inventories SET reserved_quantity=2,version=7 WHERE sku_id=?", order.firstSku());
      jdbc.update("UPDATE inventories SET reserved_quantity=1,version=7 WHERE sku_id=?", order.secondSku());
    });
  }

  @AfterEach
  void removeDueCandidate() {
    // A rollback test intentionally leaves READY; do not leak an overdue candidate to later suites.
    jdbc.update("UPDATE payments SET expires_at='2099-01-01' WHERE id=?", order.payment());
  }

  @Test
  void twoWorkersSelectSameCandidateButOnlyOneExpiresAndReleases() throws Exception {
    var selected = new CountDownLatch(2);
    doAnswer(invocation -> {
      Object result = invocation.callRealMethod();
      assertThat(result).isEqualTo(List.of(order.payment()));
      selected.countDown();
      assertThat(selected.await(10, TimeUnit.SECONDS)).isTrue();
      return result;
    }).when(rows).findDuePaymentIds(1);
    Set<Long> connections = ConcurrentHashMap.newKeySet();
    var atLock = new CountDownLatch(2);
    doAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
      atLock.countDown();
      assertThat(atLock.await(10, TimeUnit.SECONDS)).isTrue();
      return invocation.callRealMethod();
    }).when(rows).findForUpdate(order.payment());

    var outcomes = AfterSalesFixtures.race(() -> service.expireDue(1), () -> service.expireDue(1));
    assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
    assertThat(outcomes).extracting(AfterSalesFixtures.Outcome::value).containsExactlyInAnyOrder(1, 0);
    assertThat(connections).hasSize(2);
    verify(rows, times(1)).releaseCoupon(order.order());
    verify(rows, times(1)).markExpired(order.payment(), order.order());
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      assertThat(jdbc.queryForMap("SELECT status,provider_status,failure_code FROM payments WHERE id=?", order.payment()))
          .containsEntry("status", "FAILED").containsEntry("provider_status", "EXPIRED")
          .containsEntry("failure_code", "CHECKOUT_EXPIRED");
      assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order.order())).isEqualTo("EXPIRED");
      assertReleased(order.firstSku(), 2);
      assertReleased(order.secondSku(), 1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=?", Integer.class, order.payment())).isEqualTo(2);
      assertThat(jdbc.queryForMap("SELECT status,reserved_order_id,used_at FROM member_coupons WHERE id=?", order.coupon()))
          .containsEntry("status", "AVAILABLE").containsEntry("reserved_order_id", null).containsEntry("used_at", null);
    });
    assertThat(processor.expire(order.payment())).isFalse();
  }

  @Test
  void failureAtMarkExpiredRollsBackReleaseMovementCouponAndBothStatuses() {
    Snapshot before = snapshot();
    doAnswer(invocation -> {
      // Explicit flush makes the mutation and movement visible to SQL inside this transaction.
      entities.flush();
      assertReleased(order.firstSku(), 2);
      assertReleased(order.secondSku(), 1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE payment_id=?", Integer.class, order.payment())).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT status FROM member_coupons WHERE id=?", String.class, order.coupon())).isEqualTo("AVAILABLE");
      invocation.callRealMethod();
      entities.flush();
      assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order.order())).isEqualTo("EXPIRED");
      throw new IllegalStateException("expiry completion failure");
    }).when(rows).markExpired(order.payment(), order.order());

    assertThatThrownBy(() -> service.expireDue(1))
        .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
        .hasRootCauseInstanceOf(IllegalStateException.class).hasRootCauseMessage("expiry completion failure");
    assertThat(snapshot()).isEqualTo(before);
  }

  private void assertReleased(long sku, int quantity) {
    assertThat(jdbc.queryForMap("SELECT available_quantity,reserved_quantity,version FROM inventories WHERE sku_id=?", sku))
        .containsEntry("available_quantity", 10 + quantity).containsEntry("reserved_quantity", 0).containsEntry("version", 8L);
    assertThat(jdbc.queryForMap("SELECT type,quantity,available_before,available_after,reserved_before,reserved_after FROM inventory_movements WHERE payment_id=? AND sku_id=?", order.payment(), sku))
        .containsEntry("type", "RELEASE").containsEntry("quantity", quantity)
        .containsEntry("available_before", 10).containsEntry("available_after", 10 + quantity)
        .containsEntry("reserved_before", quantity).containsEntry("reserved_after", 0);
  }

  private Snapshot snapshot() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    return new TransactionTemplate(manager).execute(status -> new Snapshot(
        jdbc.queryForList("SELECT * FROM inventories WHERE sku_id IN (?,?) ORDER BY sku_id", order.firstSku(), order.secondSku()),
        jdbc.queryForList("SELECT * FROM inventory_movements WHERE payment_id=? ORDER BY id", order.payment()),
        jdbc.queryForMap("SELECT * FROM member_coupons WHERE id=?", order.coupon()),
        jdbc.queryForMap("SELECT * FROM payments WHERE id=?", order.payment()),
        jdbc.queryForMap("SELECT * FROM orders WHERE id=?", order.order())));
  }

  private record Snapshot(List<Map<String, Object>> inventory, List<Map<String, Object>> movements,
                          Map<String, Object> coupon, Map<String, Object> payment, Map<String, Object> order) {}
}
