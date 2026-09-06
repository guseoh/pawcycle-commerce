package com.pawcycle.backend.commerce.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CouponPatchConcurrencyIntegrationTests {
  @Autowired private CouponAdminApplicationService service;
  @Autowired private JdbcTemplate jdbc;

  private Long couponId;

  @AfterEach
  void tearDown() {
    if (couponId == null) return;
    jdbc.update(
        "DELETE FROM admin_audit_logs WHERE target_type='COUPON' AND target_id=?", couponId);
    jdbc.update("DELETE FROM member_coupons WHERE coupon_id=?", couponId);
    jdbc.update("DELETE FROM coupons WHERE id=?", couponId);
  }

  @Test
  void concurrentPartialPatchesPreserveBothChangesWithPessimisticLock() throws Exception {
    String suffix = Long.toUnsignedString(System.nanoTime());
    String initialName = "concurrent-coupon-" + suffix;
    String updatedName = "updated-coupon-" + suffix;
    jdbc.update(
        "INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,maximum_discount_amount,valid_from,valid_until,active) VALUES (?,?,?,?,?,?,?,?)",
        initialName,
        "PERCENTAGE",
        10,
        1000,
        5000,
        Timestamp.valueOf("2026-08-01 00:00:00"),
        Timestamp.valueOf("2026-09-01 00:00:00"),
        true);
    couponId =
        jdbc.queryForObject("SELECT id FROM coupons WHERE name=?", Long.class, initialName);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<?> rename = executor.submit(() -> updateTogether(namePatch(updatedName), ready, start));
      Future<?> deactivate = executor.submit(() -> updateTogether(activePatch(false), ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      rename.get(10, TimeUnit.SECONDS);
      deactivate.get(10, TimeUnit.SECONDS);
    }

    assertThat(jdbc.queryForObject("SELECT name FROM coupons WHERE id=?", String.class, couponId))
        .isEqualTo(updatedName);
    assertThat(jdbc.queryForObject("SELECT active FROM coupons WHERE id=?", Boolean.class, couponId))
        .isFalse();
  }

  private void updateTogether(
      CouponPatchCommand patch, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("concurrency start timeout");
      }
      service.update(1L, couponId, patch);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private static CouponPatchCommand namePatch(String name) {
    return new CouponPatchCommand(
        name,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true,
        false,
        false,
        false,
        false,
        false,
        false,
        false);
  }

  private static CouponPatchCommand activePatch(boolean active) {
    return new CouponPatchCommand(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        active,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        true);
  }
}
