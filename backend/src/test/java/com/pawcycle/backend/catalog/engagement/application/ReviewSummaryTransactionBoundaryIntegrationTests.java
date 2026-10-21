package com.pawcycle.backend.catalog.engagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.engagement.persistence.ReviewSummaryQueryRepository;
import com.pawcycle.backend.catalog.product.application.ProductComparisonAiClient;
import com.pawcycle.backend.catalog.product.application.ProductComparisonService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
class ReviewSummaryTransactionBoundaryIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired ReviewSummaryService reviewSummaries;
  @Autowired ReviewSummaryQueryRepository queries;
  @Autowired ProductComparisonService comparisons;
  @MockitoBean ReviewSummaryAiClient reviewAi;
  @MockitoBean ProductComparisonAiClient comparisonAi;

  private Long brandId;
  private Long categoryId;
  private Long productId;
  private Long otherProductId;
  private final List<Long> memberIds = new ArrayList<>();
  private final List<Long> reviewIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO brands(name,slug,active,display_order) VALUES (?,?,true,0)",
        "summary-brand-" + suffix, "summary-brand-" + suffix);
    brandId = id();
    jdbc.update(
        "INSERT INTO categories(name,slug,display_order,active) VALUES (?,?,0,true)",
        "summary-category-" + suffix, "summary-category-" + suffix);
    categoryId = id();
    productId = addProduct("first-" + suffix);
    otherProductId = addProduct("second-" + suffix);
    for (int index = 0; index < 4; index++) {
      jdbc.update(
          "INSERT INTO members(email,password_hash,role) VALUES (?,'fixture','USER')",
          "summary-" + index + "-" + suffix + "@example.test");
      memberIds.add(id());
    }
    for (int index = 0; index < 3; index++) addReview(index, true);
  }

  @AfterEach
  void tearDown() {
    if (productId != null) {
      jdbc.update("DELETE FROM product_review_summaries WHERE product_id=?", productId);
      jdbc.update("DELETE FROM reviews WHERE product_id=?", productId);
      jdbc.update("DELETE FROM products WHERE id=?", productId);
    }
    if (otherProductId != null) jdbc.update("DELETE FROM products WHERE id=?", otherProductId);
    for (Long memberId : memberIds) jdbc.update("DELETE FROM members WHERE id=?", memberId);
    if (categoryId != null) jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
    if (brandId != null) jdbc.update("DELETE FROM brands WHERE id=?", brandId);
  }

  @Test
  void bothAiCallsRunOutsideActualTransactionsWithCanonicalData() {
    when(reviewAi.summarize(anyList()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat((List<?>) call.getArgument(0)).hasSize(3);
              return "공개 리뷰에 긍정적인 반응이 많습니다.";
            });
    var summary = reviewSummaries.summary(productId);
    assertThat(summary.status()).isEqualTo("AVAILABLE");
    assertThat(summary.reviewCount()).isEqualTo(3L);

    when(comparisonAi.compare(anyList()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat((List<?>) call.getArgument(0)).hasSize(2);
              return "두 상품의 특징이 다릅니다.";
            });
    var comparison = comparisons.compare(List.of(productId, otherProductId));
    assertThat(comparison.aiStatus()).isEqualTo("AVAILABLE");
    assertThat(comparison.products())
        .extracting(facts -> facts.productId())
        .containsExactly(productId, otherProductId);
  }

  @Test
  void aiWaitDoesNotBlockReviewWriteAndLateOldResultCannotReplaceFreshCache()
      throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    when(reviewAi.summarize(anyList()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              if (calls.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("AI latch timeout");
                return "이전 리뷰에 대한 내용입니다.";
              }
              return "변경된 리뷰 내용을 반영했습니다.";
            });

    try (var workers = Executors.newFixedThreadPool(2)) {
      var oldRequest = workers.submit(() -> reviewSummaries.summary(productId));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var write =
            workers.submit(
                () ->
                    jdbc.update(
                        "UPDATE reviews SET content=?,updated_at=? WHERE id=?",
                        "새로 수정한 리뷰 내용",
                        Timestamp.from(Instant.parse("2026-10-21T00:00:00Z")),
                        reviewIds.getFirst()));
        assertThat(write.get(5, TimeUnit.SECONDS)).isEqualTo(1);

        var newer = reviewSummaries.summary(productId);
        assertThat(newer.status()).isEqualTo("AVAILABLE");
        assertThat(newer.summary()).isEqualTo("변경된 리뷰 내용을 반영했습니다.");

        release.countDown();
        var stale = oldRequest.get(10, TimeUnit.SECONDS);
        assertThat(stale.status()).isEqualTo("UNAVAILABLE");
        assertThat(stale.summary()).isNull();
        assertThat(queries.cachedSummary(productId))
            .get()
            .extracting(ReviewSummaryQueryRepository.CachedSummary::summary)
            .isEqualTo(newer.summary());
      } finally {
        release.countDown();
      }
    }
  }

  @Test
  void guardedWriteRejectsUpdatesVisibilityInsertAndDeleteAfterSnapshot() {
    var original = queries.snapshot(productId);
    jdbc.update(
        "UPDATE reviews SET rating=1, content=?,updated_at=? WHERE id=?",
        "수정된 리뷰",
        Timestamp.from(Instant.parse("2026-10-21T00:01:00Z")),
        reviewIds.getFirst());
    assertRejected(original.sourceReviews());

    var afterUpdate = queries.snapshot(productId);
    jdbc.update("UPDATE reviews SET visible=false WHERE id=?", reviewIds.get(1));
    assertRejected(afterUpdate.sourceReviews());

    var afterHide = queries.snapshot(productId);
    addReview(3, true);
    assertRejected(afterHide.sourceReviews());

    var afterInsert = queries.snapshot(productId);
    jdbc.update("DELETE FROM reviews WHERE id=?", reviewIds.get(2));
    assertRejected(afterInsert.sourceReviews());
    assertThat(queries.cachedSummary(productId)).isEmpty();
  }

  private void assertRejected(List<ReviewSummaryQueryRepository.ReviewRow> expected) {
    assertThat(
            queries.saveSummaryIfCurrent(
                productId,
                expected,
                "f".repeat(64),
                "저장하면 안 되는 캐시",
                Timestamp.from(Instant.parse("2026-10-21T00:02:00Z"))))
        .isFalse();
  }

  private long addProduct(String catalogKey) {
    jdbc.update(
        """
        INSERT INTO products(brand_id,catalog_key,category_id,name,short_description,
                             description,pet_type,thumbnail_url,display_status)
        VALUES (?,?,?,'fixture','review summary',NULL,'DOG',NULL,'PUBLIC')
        """,
        brandId, catalogKey, categoryId);
    return id();
  }

  private void addReview(int memberIndex, boolean visible) {
    Timestamp time = Timestamp.from(Instant.parse("2026-10-01T00:00:00Z"));
    jdbc.update(
        """
        INSERT INTO reviews(product_id,member_id,rating,content,visible,created_at,updated_at)
        VALUES (?,?,5,?,?,?,?)
        """,
        productId, memberIds.get(memberIndex), "리뷰 " + memberIndex, visible, time, time);
    reviewIds.add(id());
  }

  private long id() {
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }
}
