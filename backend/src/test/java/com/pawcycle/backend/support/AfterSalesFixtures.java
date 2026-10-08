package com.pawcycle.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Committed, isolated fixtures: service calls and racing workers own their real MySQL transactions. */
public class AfterSalesFixtures {
  private final JdbcTemplate jdbc;
  private final EntityManager entities;
  private final TransactionTemplate transaction;

  public AfterSalesFixtures(JdbcTemplate jdbc, EntityManager entities, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.entities = entities;
    this.transaction = new TransactionTemplate(manager);
  }

  public OrderFixture paidOrder(String delivery, BigDecimal amount) {
    return transaction.execute(status -> {
      var f = new SecondaryReadFixtures(jdbc, entities);
      long member = f.member();
      long admin = f.member();
      jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
      var product = f.product(f.category(false), f.brand(true), "DOG", "PUBLIC");
      var first = f.sku(product, SkuStatus.ACTIVE, 10);
      var second = f.sku(product, SkuStatus.ACTIVE, 10);
      long order = f.order(member, "ONE_TIME", "PAID", amount, SecondaryReadFixtures.stamp());
      // Insert in reverse SKU order; compensation must still use SKU ordering.
      f.item(order, second);
      f.item(order, first);
      jdbc.update("UPDATE order_items SET quantity=2 WHERE order_id=? AND sku_id=?", order, first.getId());
      long payment = f.payment(order, "NORMAL", "SUCCEEDED", 1);
      Timestamp delivered = Timestamp.from(Instant.now().minusSeconds(86400));
      jdbc.update("INSERT INTO deliveries(order_id,status,shipped_at,delivered_at) VALUES (?,?,?,?)",
          order, delivery, "PREPARING".equals(delivery) ? null : delivered,
          "DELIVERED".equals(delivery) ? delivered : null);
      jdbc.update("INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,valid_from,valid_until,active) VALUES ('T07 fixture','FIXED_AMOUNT',1,0,'2026-01-01','2027-01-01',true)");
      long coupon = f.lastId();
      jdbc.update("INSERT INTO member_coupons(member_id,coupon_id,status,reserved_order_id,issued_at,used_at) VALUES (?,?,'USED',?,?,?)",
          member, coupon, order, SecondaryReadFixtures.stamp(), SecondaryReadFixtures.stamp());
      return new OrderFixture(member, admin, order, payment, first.getId(), second.getId(), f.lastId());
    });
  }

  public int integer(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
  public long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
  public String text(String sql, Object... args) { return jdbc.queryForObject(sql, String.class, args); }

  public static List<Outcome> race(Callable<?> first, Callable<?> second) throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> run(first, ready, start));
      var b = executor.submit(() -> run(second, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
    }
  }

  private static Outcome run(Callable<?> call, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race start timeout");
    try { return new Outcome(call.call(), null); }
    catch (CommerceException exception) { return new Outcome(null, exception); }
    // Unexpected persistence/deadlock errors fail the future instead of counting as expected conflicts.
  }

  public record Outcome(Object value, CommerceException error) {}
  public record OrderFixture(long member, long admin, long order, long payment, long firstSku, long secondSku, long coupon) {}
}
