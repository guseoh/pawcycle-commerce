package com.pawcycle.backend.catalog.engagement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
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
class ReviewSummaryUpsertIntegrationTests {
  @Autowired private ReviewSummaryQueryRepository summaries;
  @Autowired private JdbcTemplate jdbc;

  private Long productId;
  private Long brandId;
  private Long categoryId;

  @AfterEach
  void tearDown() {
    if (productId != null) {
      jdbc.update("DELETE FROM product_review_summaries WHERE product_id=?", productId);
      jdbc.update("DELETE FROM products WHERE id=?", productId);
    }
    if (categoryId != null) jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
    if (brandId != null) jdbc.update("DELETE FROM brands WHERE id=?", brandId);
  }

  @Test
  void concurrentFirstWritesRemainSingleRowAtomicUpsert() throws Exception {
    String suffix = Long.toUnsignedString(System.nanoTime());
    jdbc.update(
        "INSERT INTO brands(name,slug,active,display_order) VALUES (?,?,true,0)",
        "summary-brand-" + suffix,
        "summary-brand-" + suffix);
    brandId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO categories(name,slug,display_order,active) VALUES (?,?,0,true)",
        "summary-category-" + suffix,
        "summary-category-" + suffix);
    categoryId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        """
        INSERT INTO products(brand_id,catalog_key,category_id,name,short_description,description,pet_type,thumbnail_url,display_status)
        VALUES (?, ?, ?, ?, 'review summary', NULL, 'DOG', NULL, 'PUBLIC')
        """,
        brandId,
        "summary-product-" + suffix,
        categoryId,
        "summary-product-" + suffix);
    productId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Timestamp generatedAt = Timestamp.from(Instant.parse("2026-09-06T00:00:00Z"));
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<?> first =
          executor.submit(
              () -> saveTogether("a".repeat(64), "first", generatedAt, ready, start));
      Future<?> second =
          executor.submit(
              () -> saveTogether("b".repeat(64), "second", generatedAt, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_review_summaries WHERE product_id=?",
                Long.class,
                productId))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT summary FROM product_review_summaries WHERE product_id=?",
                String.class,
                productId))
        .isIn("first", "second");
  }

  private void saveTogether(
      String fingerprint,
      String summary,
      Timestamp generatedAt,
      CountDownLatch ready,
      CountDownLatch start) {
    ready.countDown();
    try {
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("concurrency start timeout");
      }
      summaries.saveSummary(productId, fingerprint, summary, generatedAt);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
