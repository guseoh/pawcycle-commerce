package com.pawcycle.backend.catalog.engagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.engagement.persistence.ReviewSummaryQueryRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewSummaryServiceTests {
  @Test
  void fewerThanThreeVisibleReviewsDoesNotCallAi() {
    ReviewSummaryQueryRepository queries = mock(ReviewSummaryQueryRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
    when(products.findPublicById(1L)).thenReturn(Optional.of(mock(Product.class)));
    when(queries.hasActiveBrand(1L)).thenReturn(true);
    when(queries.latestReviews(1L)).thenReturn(List.of());
    when(queries.visibleReviewCount(1L)).thenReturn(2L);
    when(queries.visibleAverageRating(1L)).thenReturn(new BigDecimal("4.50"));

    ReviewSummaryResponse response =
        new ReviewSummaryService(
                queries,
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
    ReviewSummaryQueryRepository queries = mock(ReviewSummaryQueryRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
    ReviewSummaryService service =
        new ReviewSummaryService(
            queries, products, ai, Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));
    List<ReviewSummaryService.ReviewRow> reviews = reviews(3);
    stubSummary(
        queries,
        products,
        reviews,
        reviews,
        Optional.of(
            new ReviewSummaryQueryRepository.CachedSummary(
                service.fingerprint(reviews), "좋은 품질의 상품입니다.")));

    ReviewSummaryResponse response = service.summary(1L);

    assertThat(response.status()).isEqualTo("AVAILABLE");
    assertThat(response.summary()).isEqualTo("좋은 품질의 상품입니다.");
    verifyNoInteractions(ai);
  }

  @Test
  void validAiResultIsStoredAndInvalidOrExceptionIsUnavailable() {
    for (String aiOutput : List.of("상품의 만족도가 높습니다.", "<script>위험</script>")) {
      ReviewSummaryQueryRepository queries = mock(ReviewSummaryQueryRepository.class);
      ProductRepository products = mock(ProductRepository.class);
      ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
      ReviewSummaryService service =
          new ReviewSummaryService(
              queries,
              products,
              ai,
              Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));
      List<ReviewSummaryService.ReviewRow> reviews = reviews(3);
      stubSummary(queries, products, reviews, reviews, Optional.empty());
      when(ai.summarize(org.mockito.ArgumentMatchers.anyList())).thenReturn(aiOutput);

      ReviewSummaryResponse response = service.summary(1L);

      if (aiOutput.startsWith("<")) {
        assertThat(response.status()).isEqualTo("UNAVAILABLE");
      } else {
        assertThat(response.status()).isEqualTo("AVAILABLE");
        verify(queries).saveSummary(
            eq(1L),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Timestamp.class));
      }
    }

    ReviewSummaryQueryRepository queries = mock(ReviewSummaryQueryRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
    List<ReviewSummaryService.ReviewRow> reviews = reviews(3);
    stubSummary(queries, products, reviews, reviews, Optional.empty());
    when(ai.summarize(org.mockito.ArgumentMatchers.anyList()))
        .thenThrow(new IllegalStateException("provider unavailable"));
    assertThat(new ReviewSummaryService(queries, products, ai, Clock.systemUTC()).summary(1L).status())
        .isEqualTo("UNAVAILABLE");
  }

  @Test
  void visibleReviewOutsideLatestAiWindowInvalidatesSummaryFingerprint() {
    ReviewSummaryQueryRepository queries = mock(ReviewSummaryQueryRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
    ReviewSummaryService service = new ReviewSummaryService(queries, products, ai, Clock.systemUTC());
    List<ReviewSummaryService.ReviewRow> latest = reviews(30);
    List<ReviewSummaryService.ReviewRow> changedFull = new ArrayList<>(latest);
    changedFull.add(
        new ReviewSummaryService.ReviewRow(31L, 5, "새로운 visible review", timestamp(31)));
    stubSummary(
        queries,
        products,
        latest,
        changedFull,
        Optional.of(new ReviewSummaryQueryRepository.CachedSummary(service.fingerprint(latest), "오래된 요약")));
    when(ai.summarize(org.mockito.ArgumentMatchers.anyList())).thenReturn("전체 리뷰가 긍정적입니다.");

    ReviewSummaryResponse response = service.summary(1L);

    assertThat(response.status()).isEqualTo("AVAILABLE");
    verify(ai).summarize(org.mockito.ArgumentMatchers.anyList());
  }

  private void stubSummary(
      ReviewSummaryQueryRepository queries,
      ProductRepository products,
      List<ReviewSummaryService.ReviewRow> latest,
      List<ReviewSummaryService.ReviewRow> all,
      Optional<ReviewSummaryQueryRepository.CachedSummary> cache) {
    when(products.findPublicById(1L)).thenReturn(Optional.of(mock(Product.class)));
    when(queries.hasActiveBrand(1L)).thenReturn(true);
    when(queries.latestReviews(1L)).thenReturn(toQueryRows(latest));
    when(queries.allReviews(1L)).thenReturn(toQueryRows(all));
    when(queries.visibleReviewCount(1L)).thenReturn((long) all.size());
    when(queries.visibleAverageRating(1L)).thenReturn(new BigDecimal("4.50"));
    when(queries.cachedSummary(1L)).thenReturn(cache);
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

  private List<ReviewSummaryQueryRepository.ReviewRow> toQueryRows(
      List<ReviewSummaryService.ReviewRow> rows) {
    return rows.stream()
        .map(row -> new ReviewSummaryQueryRepository.ReviewRow(row.id(), row.rating(), row.content(), row.updatedAt()))
        .toList();
  }
}
