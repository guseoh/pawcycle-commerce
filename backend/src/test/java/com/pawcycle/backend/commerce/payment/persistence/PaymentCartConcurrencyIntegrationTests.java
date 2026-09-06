package com.pawcycle.backend.commerce.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class PaymentCartConcurrencyIntegrationTests {
  @Autowired private PaymentPersistenceAdapter payments;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;

  private Long memberId;

  @AfterEach
  void tearDown() {
    if (memberId != null) {
      jdbc.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE member_id=?)", memberId);
      jdbc.update("DELETE FROM carts WHERE member_id=?", memberId);
      jdbc.update("DELETE FROM members WHERE id=?", memberId);
    }
  }

  @Test
  void concurrentMissingCartConsumeCreatesExactlyOneCart() throws Exception {
    String suffix = Long.toUnsignedString(System.nanoTime());
    jdbc.update(
        "INSERT INTO members(email,password_hash,role) VALUES (?,?,'USER')",
        "payment-cart-" + suffix + "@example.com",
        "not-a-real-password-hash");
    memberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<?> first = executor.submit(() -> consumeTogether(ready, start));
      Future<?> second = executor.submit(() -> consumeTogether(ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM carts WHERE member_id=?", Long.class, memberId))
        .isEqualTo(1L);
  }

  private void consumeTogether(CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("concurrency start timeout");
      }
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(status -> payments.consumeCart(memberId, Long.MAX_VALUE));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
