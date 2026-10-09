package com.pawcycle.backend.subscription.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.payment.domain.PaymentEntity;
import com.pawcycle.backend.commerce.payment.persistence.LegacyPaymentReconciliationPersistenceAdapter;
import com.pawcycle.backend.commerce.payment.persistence.PaymentReconciliationPersistenceAdapter;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import com.pawcycle.backend.support.MysqlReadView;
import com.pawcycle.backend.support.MysqlLockObservation;
import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Same committed fixture, rollback-isolated JDBC/JPA runs on MySQL 8.4; no provider is used. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=3", "spring.datasource.hikari.minimum-idle=0"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BillingRecoveryPersistenceParityIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager manager;
  @Autowired SubscriptionBillingPersistence billing;
  @Autowired SubscriptionBillingRetryPersistence retry;
  @Autowired PaymentReconciliationPersistenceAdapter reconciliation;
  private TransactionTemplate tx;
  private long member, order, payment, sku, missingInventorySku, schedule, cart, coupon;
  private long secondSku, subscription, product, category, brand, couponDefinition, billingMethod;
  private long firstOrderItem, secondOrderItem;
  private final Timestamp time = Timestamp.valueOf("2026-09-18 23:59:59.123456");
  private final Clock clock = Clock.fixed(Instant.parse("2026-09-18T14:59:59.123456Z"), ZoneOffset.UTC);
  private LegacySubscriptionBillingPersistence oldBilling;
  private LegacySubscriptionBillingRetryPersistence oldRetry;
  private LegacyPaymentReconciliationPersistenceAdapter oldReconciliation;

  @BeforeEach
  void fixture() {
    assertThat(jdbc.queryForObject("SELECT @@version", String.class)).startsWith("8.4.");
    tx = new TransactionTemplate(manager);
    oldBilling = new LegacySubscriptionBillingPersistence(jdbc);
    oldRetry = new LegacySubscriptionBillingRetryPersistence(jdbc);
    oldReconciliation = new LegacyPaymentReconciliationPersistenceAdapter(jdbc, clock);
    // Give both implementations exactly the same fixed completion clock.
    reconciliation = new PaymentReconciliationPersistenceAdapter(entities, clock);
    tx.executeWithoutResult(status -> {
      var f = new SecondaryReadFixtures(jdbc, entities);
      member = f.member();
      var category = f.category(false);
      this.category = category.getId();
      var brand = f.brand(true);
      this.brand = brand.getId();
      var product = f.product(category, brand, "DOG", "PUBLIC");
      this.product = product.getId();
      var first = f.sku(product, SkuStatus.ACTIVE, 10);
      var second = f.sku(product, SkuStatus.ACTIVE, 10);
      missingInventorySku = f.sku(product, SkuStatus.ACTIVE, null).getId();
      sku = first.getId();
      secondSku = second.getId();
      order = f.order(member, "SUBSCRIPTION", "PAYMENT_PENDING", new BigDecimal("1234.56"), null);
      f.item(order, second);
      secondOrderItem = f.lastId();
      f.item(order, first);
      firstOrderItem = f.lastId();
      payment = f.payment(order, "BILLING", "READY", 1);
      jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,status,version) VALUES (?,?,1,2,'2026-09-01','2026-09-18','ACTIVE',0)", member, sku);
      subscription = f.lastId();
      jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status) VALUES (?,'2026-09-18','SCHEDULED')", subscription);
      schedule = f.lastId();
      jdbc.update("INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,scheduled_date) VALUES (?,?,?,'2026-09-18')", order, subscription, schedule);
      jdbc.update("INSERT INTO billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at) VALUES (?,'TOSS',?,?,'ACTIVE',?)", member, SecondaryReadFixtures.unique(), "fixture-only", time);
      billingMethod = f.lastId();
      jdbc.update("INSERT INTO carts(member_id,version,created_at,updated_at) VALUES (?,7,?,?)", member, time, time);
      cart = f.lastId();
      jdbc.update("INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?,?,5)", cart, sku);
      jdbc.update("INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,valid_from,valid_until,active) VALUES ('T10','FIXED_AMOUNT',1,0,'2026-01-01','2027-01-01',true)");
      couponDefinition = f.lastId();
      jdbc.update("INSERT INTO member_coupons(member_id,coupon_id,status,reserved_order_id,issued_at) VALUES (?,?,'RESERVED',?,?)", member, couponDefinition, order, time);
      coupon = f.lastId();
    });
  }

  @AfterEach
  void cleanFixtures() {
    if (tx == null) return; // Version/setup failure before the fixture transaction starts.
    tx.executeWithoutResult(status -> {
      entities.clear();
      // Parent IDs also cover an unexpected committed retry/contender owned by this fixture.
      jdbc.update("DELETE FROM payments WHERE order_id=?", order);
      jdbc.update("DELETE FROM order_items WHERE id IN (?,?)", firstOrderItem, secondOrderItem);
      jdbc.update("DELETE FROM subscription_order_context WHERE order_id=?", order);
      jdbc.update("DELETE FROM member_coupons WHERE id=?", coupon);
      jdbc.update("DELETE FROM cart_items WHERE cart_id=?", cart);
      jdbc.update("DELETE FROM carts WHERE id=?", cart);
      jdbc.update("DELETE FROM orders WHERE id=?", order);
      jdbc.update("DELETE FROM subscription_schedules WHERE id=?", schedule);
      jdbc.update("DELETE FROM subscriptions WHERE id=?", subscription);
      jdbc.update("DELETE FROM billing_payment_methods WHERE id=? OR member_id=?", billingMethod, member);
      jdbc.update("DELETE FROM inventories WHERE sku_id IN (?,?,?)", sku, secondSku, missingInventorySku);
      jdbc.update("DELETE FROM skus WHERE id IN (?,?,?)", sku, secondSku, missingInventorySku);
      jdbc.update("DELETE FROM products WHERE id=?", product);
      jdbc.update("DELETE FROM categories WHERE id=?", category);
      jdbc.update("DELETE FROM brands WHERE id=?", brand);
      jdbc.update("DELETE FROM coupons WHERE id=?", couponDefinition);
      jdbc.update("DELETE FROM members WHERE id=?", member);
    });
  }

  @Test
  void allTypedReadsMatchFrozenSqlIncludingNullAndMissingRows() {
    rolledBack(() -> {
      for (long id : List.of(payment, Long.MAX_VALUE)) {
        equalRead(oldBilling, billing, "lockWork", id);
        equalRead(oldBilling, billing, "lockProcessingPayment", id);
        equalRead(oldRetry, retry, "lockRetryAttempt", id);
        equalRead(oldRetry, retry, "lockFailureAttempt", id);
        equalRead(oldRetry, retry, "lockReconciliation", id);
        equalRead(oldReconciliation, reconciliation, "findForStart", id);
        equalRead(oldReconciliation, reconciliation, "findForCompletion", id);
        equalRead(oldReconciliation, reconciliation, "find", id);
      }
      for (long id : List.of(order, Long.MAX_VALUE)) {
        equalRead(oldBilling, billing, "findOrderedItems", id);
        equalRead(oldRetry, retry, "findOrderedItems", id);
        equalRead(oldRetry, retry, "findItems", id);
        equalRead(oldRetry, retry, "lockOrder", id);
        equalRead(oldRetry, retry, "findExistingAttempt", id, 1);
        equalRead(oldRetry, retry, "findExistingAttempt", id, 2);
        equalRead(oldReconciliation, reconciliation, "findOrderItems", id);
      }
      equalRead(oldBilling, billing, "findCandidates");
      equalRead(oldRetry, retry, "lockInventory", sku);
      equalRead(oldRetry, retry, "lockInventory", Long.MAX_VALUE);
      equalRead(oldRetry, retry, "countStockHolds", schedule);
      equalRead(oldRetry, retry, "countStockHolds", Long.MAX_VALUE);
      jdbc.update("UPDATE payments SET status='PROCESSING' WHERE id=?", payment);
      equalRead(oldBilling, billing, "lockProcessingPayment", payment);
      jdbc.update("DELETE FROM billing_payment_methods WHERE member_id=?", member);
      equalRead(oldBilling, billing, "lockWork", payment);
      jdbc.update("DELETE FROM subscription_order_context WHERE order_id=?", order);
      equalRead(oldBilling, billing, "lockWork", payment);
      equalRead(oldRetry, retry, "lockRetryAttempt", payment);
      return null;
    });
  }

  @Test
  void everyFieldWriteAffectedRowAndReadAfterWriteMatchesAndRollsBack() {
    var before = digest();
    var expected = rolledBack(() -> mutations(oldBilling, oldRetry, oldReconciliation));
    var actual = rolledBack(() -> mutations(billing, retry, reconciliation));
    assertThat(actual).isEqualTo(expected);
    assertThat(digest()).isEqualTo(before);
  }

  private List<Object> mutations(Object bill, Object retries, Object recover) {
    var result = new ArrayList<Object>();
    for (long id : List.of(payment, Long.MAX_VALUE)) {
      result.add(call(bill, "markUnknown", null, id)); // wrong state then PROCESSING
      result.add(call(bill, "markProcessing", id));
      result.add(call(bill, "markProcessing", id)); // no-op matched row
      result.add(call(bill, "markUnknown", "CUSTOM", id));
      result.add(call(bill, "markUnknown", "CUSTOM", id)); // CAS rejects UNKNOWN
      result.add(call(bill, "markSucceeded", null, time, id));
      result.add(call(retries, "markFailed", "DECLINED", "CODE", time, id));
      result.add(call(retries, "recordReconciliation", 10, time, id));
      result.add(call(retries, "markPaymentOrderActionRequired", id));
      call(recover, "incrementAttempts", id, 9);
      result.add(call(recover, "find", id));
      call(recover, "markSucceeded", id, "CUSTOM_DONE", time);
      call(recover, "markFailed", id, "CUSTOM_FAIL");
    }
    for (long id : List.of(order, Long.MAX_VALUE)) {
      result.add(call(bill, "markOrderPaid", time, id));
      result.add(call(retries, "markOrderActionRequired", id));
      call(recover, "markOrderPaid", id, time);
      call(recover, "markOrderPaymentFailed", id);
      call(recover, "markOrderActionRequired", id);
      call(recover, "useReservedCoupon", id, time);
      call(recover, "releaseReservedCoupon", id); // USED does not release
      jdbc.update("UPDATE member_coupons SET status='RESERVED' WHERE id=?", coupon);
      call(recover, "releaseReservedCoupon", id);
    }
    for (long id : List.of(schedule, Long.MAX_VALUE)) {
      result.add(call(bill, "releaseMissingMethodHold", id));
      result.add(call(bill, "holdMissingMethod", id));
      result.add(call(retries, "releaseStockHold", id)); // different reason
      result.add(call(bill, "releaseMissingMethodHold", id));
      result.add(call(retries, "holdStockUnavailable", id));
      result.add(call(retries, "countStockHolds", id));
      result.add(call(retries, "releaseStockHold", id));
      result.add(call(retries, "releaseStockHold", id));
      result.add(call(retries, "holdRetryExhausted", id));
    }
    entities.flush();
    result.add(digest());
    return result;
  }

  @ParameterizedTest
  @ValueSource(ints = {-2, -1, 1, 2, 5})
  void cartAbsentItemAbsentCartDeleteDecrementVersionAndTimestampMatch(int quantity) {
    var expected = rolledBack(() -> cartDigest(oldReconciliation, quantity));
    assertThat(rolledBack(() -> cartDigest(reconciliation, quantity))).isEqualTo(expected);
  }

  private Object cartDigest(Object store, int quantity) {
    if (quantity < 0) jdbc.update("DELETE FROM cart_items WHERE cart_id=?", cart);
    if (quantity == -2) jdbc.update("DELETE FROM carts WHERE id=?", cart);
    if (quantity > 0) jdbc.update("UPDATE cart_items SET quantity=? WHERE cart_id=?", quantity, cart);
    jdbc.update("UPDATE order_items SET quantity=2 WHERE order_id=?", order);
    call(store, "consumeCart", member, order);
    return digest();
  }

  @Test
  void identityDefaultsDecimalsMicrosecondsAndRollbackAfterFlushMatch() {
    var before = digest();
    var expected = rolledBack(() -> insertDigest(true));
    assertThat(rolledBack(() -> insertDigest(false))).isEqualTo(expected);
    assertThat(digest()).isEqualTo(before);
    rolledBack(() -> {
      entities.find(PaymentEntity.class, payment);
      retry.markFailed(null, "ONLY_FIELD_WRITES", time, payment);
      assertThat(entities.find(PaymentEntity.class, payment).getStatus()).isEqualTo("FAILED");
      assertThat(entities.find(PaymentEntity.class, payment).getProviderStatus()).isNull();
      return null;
    });
  }

  private Object insertDigest(boolean legacy) {
    String providerId = SecondaryReadFixtures.unique();
    String key = SecondaryReadFixtures.unique();
    BigDecimal amount = new BigDecimal("9007199254740991.12");
    long id;
    if (legacy) {
      assertThat(oldRetry.insertAttempt(order, amount, providerId, key, 3, time, time)).isEqualTo(1);
      id = oldRetry.lastInsertedId();
    } else id = retry.insertAttempt(order, amount, providerId, key, 3, time, time);
    assertThat(id).isPositive();
    assertThat(retry.findExistingAttempt(order, 3)).isEqualTo(id);
    return jdbc.queryForMap("SELECT order_id,type,provider,status,amount,payment_key,provider_status,attempt_no,failure_code,failure_message,CAST(requested_at AS CHAR) requested_at,CAST(created_at AS CHAR) created_at,approved_at,failed_at,expires_at,reconciliation_attempts,last_reconciled_at,succeeded_order_id FROM payments WHERE id=?", id);
  }

  @ParameterizedTest
  @ValueSource(strings = {"attempt", "provider", "idempotency", "success"})
  void uniqueCollisionsRollbackBothImplementationsWithSameSqlState(String collision) {
    for (boolean legacy : List.of(true, false)) {
      var before = digest();
      assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
        if (collision.equals("success")) {
          oldRetry.insertAttempt(order, BigDecimal.ONE, SecondaryReadFixtures.unique(), SecondaryReadFixtures.unique(), 2, time, time);
          long second = oldRetry.lastInsertedId();
          if (legacy) { oldBilling.markSucceeded(null, time, payment); oldBilling.markSucceeded(null, time, second); }
          else { billing.markSucceeded(null, time, payment); billing.markSucceeded(null, time, second); }
        } else {
          String providerId = collision.equals("provider") ? jdbc.queryForObject("SELECT provider_order_id FROM payments WHERE id=?", String.class, payment) : SecondaryReadFixtures.unique();
          String key = collision.equals("idempotency") ? jdbc.queryForObject("SELECT idempotency_key FROM payments WHERE id=?", String.class, payment) : SecondaryReadFixtures.unique();
          int attempt = collision.equals("attempt") ? 1 : 2;
          if (legacy) oldRetry.insertAttempt(order, BigDecimal.ONE, providerId, key, attempt, time, time);
          else retry.insertAttempt(order, BigDecimal.ONE, providerId, key, attempt, time, time);
        }
      })).satisfies(error -> {
        Throwable cause = error;
        while (!(cause instanceof SQLException) && cause.getCause() != null) cause = cause.getCause();
        assertThat(cause).isInstanceOf(SQLException.class);
        assertThat(((SQLException) cause).getSQLState()).isEqualTo("23000");
      });
      assertThat(digest()).isEqualTo(before);
    }
  }

  @Test
  void physicalRecordIndexGapFootprintsAndCompetingWritesMatch() throws Exception {
    MysqlLockObservation.requireAccess(jdbc);
    Object[][] locks = {
      {oldBilling, billing, "lockWork", payment},
      {oldBilling, billing, "lockProcessingPayment", payment},
      {oldRetry, retry, "lockRetryAttempt", payment},
      {oldRetry, retry, "lockFailureAttempt", payment},
      {oldRetry, retry, "lockReconciliation", payment},
      {oldRetry, retry, "lockOrder", order},
      {oldRetry, retry, "lockInventory", sku},
      {oldRetry, retry, "lockInventory", Long.MAX_VALUE},
      {oldReconciliation, reconciliation, "findForStart", payment},
      {oldReconciliation, reconciliation, "findForCompletion", payment},
      {oldReconciliation, reconciliation, "consumeCart", member, order}
    };
    jdbc.update("UPDATE payments SET status='PROCESSING' WHERE id=?", payment);
    for (Object[] entry : locks) {
      var args = java.util.Arrays.copyOfRange(entry, 3, entry.length);
      var expected = rolledBack(() -> footprint(entry[0], (String) entry[2], args));
      assertThat(rolledBack(() -> footprint(entry[1], (String) entry[2], args)))
          .as("physical MySQL locks: %s", entry[2]).isEqualTo(expected);
      assertThat(expected).isNotEmpty();
    }
    rolledBack(() -> {
      billing.lockWork(payment);
      assertBlocked("UPDATE payments SET provider_status='contender' WHERE id=" + payment);
      assertBlocked("UPDATE orders SET status='PAYMENT_FAILED' WHERE id=" + order);
      assertBlocked("UPDATE subscription_order_context SET scheduled_date=scheduled_date WHERE order_id=" + order);
      assertBlocked("UPDATE billing_payment_methods SET status='REVOKED' WHERE member_id=" + member);
      return null;
    });
    long method = jdbc.queryForObject("SELECT id FROM billing_payment_methods WHERE member_id=?", Long.class, member);
    // Open the non-locking pre-DELETE read view before committing the deletion. It remains open
    // across both independent comparison transactions and competing INSERT checks, then rolls
    // back even on assertion failure. No lock row is filtered or normalized from either result.
    try (MysqlReadView pin = MysqlReadView.open(dataSource)) {
      jdbc.update("DELETE FROM billing_payment_methods WHERE member_id=?", member);
      var absentExpected = rolledBack(() -> footprint(oldBilling, "lockWork", payment));
      assertThat(rolledBack(() -> footprint(billing, "lockWork", payment))).isEqualTo(absentExpected);
      assertThat(absentExpected).anySatisfy(lock -> {
        assertThat(lock.get("INDEX_NAME")).isEqualTo("fk_billing_payment_methods_member");
        assertThat(lock.get("LOCK_DATA")).isEqualTo(member + ", " + method);
      });
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_locks WHERE THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=?)", Integer.class, pin.connectionId())).isZero();
      rolledBack(() -> {
        billing.lockWork(payment);
        assertBlocked("INSERT INTO billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at) VALUES (" + member + ",'TOSS','fixture-customer','fixture-key','ACTIVE','2026-09-18')");
        retry.lockInventory(Long.MAX_VALUE);
        assertBlocked("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (" + missingInventorySku + ",1,0,0)");
        return null;
      });
    }
  }

  private List<java.util.Map<String, Object>> footprint(Object store, String method, Object... args) {
    call(store, method, args);
    long connection = jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class);
    return jdbc.queryForList("SELECT OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_STATUS,LOCK_DATA FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE() AND THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=?) ORDER BY OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_DATA", connection);
  }

  private void assertBlocked(String sql) {
    try (Connection contender = dataSource.getConnection(); var statement = contender.createStatement()) {
      statement.execute("SET SESSION innodb_lock_wait_timeout=1");
      assertThatThrownBy(() -> statement.executeUpdate(sql)).isInstanceOf(SQLException.class)
          .satisfies(error -> assertThat(((SQLException) error).getErrorCode()).isEqualTo(1205));
    } catch (SQLException error) { throw new AssertionError(error); }
  }

  private List<Object> digest() {
    return List.of(
        jdbc.queryForList("SELECT type,provider,status,amount,provider_status,payment_key,attempt_no,failure_code,failure_message,CAST(requested_at AS CHAR) requested_at,CAST(created_at AS CHAR) created_at,CAST(approved_at AS CHAR) approved_at,CAST(failed_at AS CHAR) failed_at,reconciliation_attempts,CAST(last_reconciled_at AS CHAR) last_reconciled_at FROM payments WHERE order_id=? ORDER BY attempt_no", order),
        jdbc.queryForMap("SELECT status,CAST(paid_at AS CHAR) paid_at FROM orders WHERE id=?", order),
        jdbc.queryForMap("SELECT status,hold_reason FROM subscription_schedules WHERE id=?", schedule),
        jdbc.queryForList("SELECT status,reserved_order_id,CAST(used_at AS CHAR) used_at FROM member_coupons WHERE id=?", coupon),
        jdbc.queryForList("SELECT version,CAST(updated_at AS CHAR) updated_at FROM carts WHERE id=?", cart),
        jdbc.queryForList("SELECT sku_id,quantity FROM cart_items WHERE cart_id=? ORDER BY sku_id", cart));
  }

  private <T> T rolledBack(Supplier<T> action) {
    return tx.execute(status -> {
      try { return action.get(); }
      finally { status.setRollbackOnly(); }
    });
  }

  private void equalRead(Object old, Object current, String method, Object... args) {
    assertThat(call(current, method, args)).as(method).isEqualTo(call(old, method, args));
  }

  private Object call(Object store, String name, Object... args) {
    try {
      for (var method : store.getClass().getMethods())
        if (method.getName().equals(name) && method.getParameterCount() == args.length) return method.invoke(store, args);
      throw new AssertionError("Missing baseline method: " + name);
    } catch (InvocationTargetException error) { throw new AssertionError(error.getCause()); }
    catch (ReflectiveOperationException error) { throw new AssertionError(error); }
  }
}
