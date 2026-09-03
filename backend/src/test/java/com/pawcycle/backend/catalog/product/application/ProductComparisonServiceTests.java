package com.pawcycle.backend.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor.RowMapper;

class ProductComparisonServiceTests {
  @Test
  void invalidProductCountIsRejectedBeforeCanonicalQueries() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new ProductComparisonService(jdbc, ai).compare(List.of(1L)))
        .isInstanceOf(ProductComparisonException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    verifyNoInteractions(jdbc, ai);
  }

  @Test
  void duplicateNullAndFourProductIdsAreRejected() {
    for (List<Long> ids :
        List.of(List.of(1L, 1L), java.util.Arrays.asList(1L, null), List.of(1L, 2L, 3L, 4L))) {
      NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
      ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> new ProductComparisonService(jdbc, ai).compare(ids))
          .isInstanceOf(ProductComparisonException.class)
          .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
      verifyNoInteractions(jdbc, ai);
    }
  }

  @Test
  void threeProductComparisonKeepsCanonicalFactsWhenAiSummaryIsValid() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);
    for (long id : List.of(1L, 2L, 3L)) stubFacts(jdbc, id);
    when(ai.compare(anyList())).thenReturn("세 상품은 가격과 구성에서 차이가 있습니다.");

    ProductComparisonResponse response =
        new ProductComparisonService(jdbc, ai).compare(List.of(1L, 2L, 3L));

    assertThat(response.aiStatus()).isEqualTo("AVAILABLE");
    assertThat(response.aiSummary()).isEqualTo("세 상품은 가격과 구성에서 차이가 있습니다.");
    assertThat(response.products())
        .extracting(ProductComparisonFacts::productId)
        .containsExactly(1L, 2L, 3L);
    assertThat(response.products())
        .allSatisfy(
            facts -> {
              assertThat(facts.representativePrice()).isEqualByComparingTo("1000.00");
              assertThat(facts.subscriptionEligible()).isTrue();
              assertThat(facts.purchasable()).isTrue();
            });
  }

  @Test
  void unsafeAiSummaryIsNotExposedOrStored() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);
    stubFacts(jdbc, 1L);
    stubFacts(jdbc, 2L);
    when(ai.compare(anyList())).thenReturn("질병 치료를 보장합니다.");

    ProductComparisonResponse response =
        new ProductComparisonService(jdbc, ai).compare(List.of(1L, 2L));

    assertThat(response.aiStatus()).isEqualTo("UNAVAILABLE");
    assertThat(response.aiSummary()).isNull();
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
        .update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
  }

  @Test
  void aiFailureLeavesCanonicalFactsAvailable() {
    NativeQueryExecutor jdbc = mock(NativeQueryExecutor.class);
    ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);
    Map<String, Object> row =
        Map.ofEntries(
            Map.entry("id", 1L),
            Map.entry("name", "상품"),
            Map.entry("thumbnail_url", "thumb"),
            Map.entry("brand_name", "브랜드"),
            Map.entry("category_name", "사료"),
            Map.entry("price", new BigDecimal("1000.00")),
            Map.entry("compare_at_price", new BigDecimal("1200.00")),
            Map.entry("average_rating", new BigDecimal("4.50")),
            Map.entry("review_count", 3L),
            Map.entry("subscription_eligible", true),
            Map.entry("purchasable", true));
    when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(row));
    when(jdbc.queryForList(anyString(), eq(2L))).thenReturn(List.of(row));
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(1L)))
        .thenReturn(List.of("size:small"));
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(2L)))
        .thenReturn(List.of("size:small"));
    when(ai.compare(anyList())).thenThrow(new IllegalStateException("provider unavailable"));

    ProductComparisonResponse response =
        new ProductComparisonService(jdbc, ai).compare(List.of(1L, 2L));

    assertThat(response.aiStatus()).isEqualTo("UNAVAILABLE");
    assertThat(response.aiSummary()).isNull();
    assertThat(response.products()).hasSize(2);
    assertThat(response.products().getFirst().representativePrice())
        .isEqualByComparingTo("1000.00");
    assertThat(response.products().getFirst().purchasable()).isTrue();
  }

  private void stubFacts(NativeQueryExecutor jdbc, long id) {
    Map<String, Object> row =
        Map.ofEntries(
            Map.entry("id", id),
            Map.entry("name", "상품" + id),
            Map.entry("thumbnail_url", "thumb"),
            Map.entry("brand_name", "브랜드"),
            Map.entry("category_name", "사료"),
            Map.entry("price", new BigDecimal("1000.00")),
            Map.entry("compare_at_price", new BigDecimal("1200.00")),
            Map.entry("average_rating", new BigDecimal("4.50")),
            Map.entry("review_count", 3L),
            Map.entry("subscription_eligible", 1),
            Map.entry("purchasable", 1));
    when(jdbc.queryForList(anyString(), eq(id))).thenReturn(List.of(row));
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(id)))
        .thenReturn(List.of("size:small"));
  }
}
