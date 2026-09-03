package com.pawcycle.backend.catalog.engagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor.RowMapper;

class ReviewSummaryServiceTests {
  @Test
  void fewerThanThreeVisibleReviewsDoesNotCallAi() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    ProductRepository products = mock(ProductRepository.class);
    ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
    when(products.findPublicById(1L)).thenReturn(Optional.of(mock(Product.class)));
    when(jdbc.queryForObject(
            org.mockito.ArgumentMatchers.contains("JOIN brands"), eq(Integer.class), eq(1L)))
        .thenReturn(1);
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(1L)))
        .thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L))).thenReturn(2L);
    when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(1L)))
        .thenReturn(new BigDecimal("4.50"));

    ReviewSummaryResponse response =
        new ReviewSummaryService(
                jdbc,
                products,
                ai,
                Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC))
            .summary(1L);

    assertThat(response.status()).isEqualTo("INSUFFICIENT_REVIEWS");
    assertThat(response.summary()).isNull();
    assertThat(response.reviewCount()).isEqualTo(2L);
    verifyNoInteractions(ai);
  }

  @Test
  void cacheHitSkipsAiAndValidAiResultIsPersisted() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    ProductRepository products = mock(ProductRepository.class);
    ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
    ReviewSummaryService service =
        new ReviewSummaryService(
            jdbc, products, ai, Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));
    List<ReviewSummaryService.ReviewRow> reviews = reviews(3);
    stubSummary(
        jdbc,
        products,
        reviews,
        reviews,
        List.of(
            Map.of(
                "source_fingerprint", service.fingerprint(reviews), "summary", "좋은 품질의 상품입니다.")));

    ReviewSummaryResponse response = service.summary(1L);

    assertThat(response.status()).isEqualTo("AVAILABLE");
    assertThat(response.summary()).isEqualTo("좋은 품질의 상품입니다.");
    verifyNoInteractions(ai);
  }

  @Test
  void validAiResultIsStoredAndInvalidOrExceptionIsUnavailable() {
    for (String aiOutput : List.of("상품의 만족도가 높습니다.", "<script>위험</script>")) {
      NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
      ProductRepository products = mock(ProductRepository.class);
      ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
      ReviewSummaryService service =
          new ReviewSummaryService(
              jdbc,
              products,
              ai,
              Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));
      List<ReviewSummaryService.ReviewRow> reviews = reviews(3);
      stubSummary(jdbc, products, reviews, reviews, List.of());
      when(ai.summarize(org.mockito.ArgumentMatchers.anyList())).thenReturn(aiOutput);

      ReviewSummaryResponse response = service.summary(1L);

      if (aiOutput.startsWith("<")) {
        assertThat(response.status()).isEqualTo("UNAVAILABLE");
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
            .update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
      } else {
        assertThat(response.status()).isEqualTo("AVAILABLE");
        verify(jdbc)
            .update(
                anyString(),
                eq(1L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Timestamp.class));
      }
    }

    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    ProductRepository products = mock(ProductRepository.class);
    ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
    List<ReviewSummaryService.ReviewRow> reviews = reviews(3);
    stubSummary(jdbc, products, reviews, reviews, List.of());
    when(ai.summarize(org.mockito.ArgumentMatchers.anyList()))
        .thenThrow(new IllegalStateException("provider unavailable"));
    assertThat(new ReviewSummaryService(jdbc, products, ai, Clock.systemUTC()).summary(1L).status())
        .isEqualTo("UNAVAILABLE");
  }

  @Test
  void visibleReviewOutsideLatestAiWindowInvalidatesSummaryFingerprint() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    ProductRepository products = mock(ProductRepository.class);
    ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
    ReviewSummaryService service = new ReviewSummaryService(jdbc, products, ai, Clock.systemUTC());
    List<ReviewSummaryService.ReviewRow> latest = reviews(30);
    List<ReviewSummaryService.ReviewRow> changedFull = new ArrayList<>(latest);
    changedFull.add(
        new ReviewSummaryService.ReviewRow(31L, 5, "새로운 visible review", timestamp(31)));
    stubSummary(
        jdbc,
        products,
        latest,
        changedFull,
        List.of(Map.of("source_fingerprint", service.fingerprint(latest), "summary", "오래된 요약")));
    when(ai.summarize(org.mockito.ArgumentMatchers.anyList())).thenReturn("전체 리뷰가 긍정적입니다.");

    ReviewSummaryResponse response = service.summary(1L);

    assertThat(response.status()).isEqualTo("AVAILABLE");
    verify(ai).summarize(org.mockito.ArgumentMatchers.anyList());
  }

  private void stubSummary(
      NativeQueryExecutor jdbc,
      ProductRepository products,
      List<ReviewSummaryService.ReviewRow> latest,
      List<ReviewSummaryService.ReviewRow> all,
      List<Map<String, Object>> cache) {
    when(products.findPublicById(1L)).thenReturn(Optional.of(mock(Product.class)));
    when(jdbc.queryForObject(
            org.mockito.ArgumentMatchers.contains("JOIN brands"), eq(Integer.class), eq(1L)))
        .thenReturn(1);
    when(jdbc.query(
            anyString(),
            org.mockito.ArgumentMatchers.<RowMapper<ReviewSummaryService.ReviewRow>>any(),
            eq(1L)))
        .thenReturn(latest, all);
    when(jdbc.queryForObject(
            org.mockito.ArgumentMatchers.contains("COUNT(*)"), eq(Long.class), eq(1L)))
        .thenReturn((long) all.size());
    when(jdbc.queryForObject(
            org.mockito.ArgumentMatchers.contains("AVG(rating)"), eq(BigDecimal.class), eq(1L)))
        .thenReturn(new BigDecimal("4.50"));
    when(jdbc.queryForList(
            org.mockito.ArgumentMatchers.contains("product_review_summaries"), eq(1L)))
        .thenReturn(cache);
  }

  private List<ReviewSummaryService.ReviewRow> reviews(int count) {
    List<ReviewSummaryService.ReviewRow> result = new ArrayList<>();
    for (int index = 1; index <= count; index++)
      result.add(new ReviewSummaryService.ReviewRow(index, 5, "좋은 상품 " + index, timestamp(index)));
    return result;
  }

  private Timestamp timestamp(int day) {
    return Timestamp.valueOf("2026-08-" + "%02d".formatted(Math.min(day, 28)) + " 00:00:00");
  }
}
