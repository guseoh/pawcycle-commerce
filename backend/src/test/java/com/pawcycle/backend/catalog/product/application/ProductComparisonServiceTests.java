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
import java.util.Optional;
import com.pawcycle.backend.catalog.product.persistence.ProductComparisonQueryRepository;
import org.junit.jupiter.api.Test;

class ProductComparisonServiceTests {
  @Test
  void invalidProductCountIsRejectedBeforeCanonicalQueries() {
    ProductComparisonQueryRepository repository = mock(ProductComparisonQueryRepository.class);
    ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new ProductComparisonService(repository, ai).compare(List.of(1L)))
        .isInstanceOf(ProductComparisonException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    verifyNoInteractions(repository, ai);
  }

  @Test
  void duplicateNullAndFourProductIdsAreRejected() {
    for (List<Long> ids :
        List.of(List.of(1L, 1L), java.util.Arrays.asList(1L, null), List.of(1L, 2L, 3L, 4L))) {
      ProductComparisonQueryRepository repository = mock(ProductComparisonQueryRepository.class);
      ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> new ProductComparisonService(repository, ai).compare(ids))
          .isInstanceOf(ProductComparisonException.class)
          .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
      verifyNoInteractions(repository, ai);
    }
  }

  @Test
  void threeProductComparisonKeepsCanonicalFactsWhenAiSummaryIsValid() {
    ProductComparisonQueryRepository repository = mock(ProductComparisonQueryRepository.class);
    ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);
    for (long id : List.of(1L, 2L, 3L)) stubFacts(repository, id);
    when(ai.compare(anyList())).thenReturn("세 상품은 가격과 구성에서 차이가 있습니다.");

    ProductComparisonResponse response =
        new ProductComparisonService(repository, ai).compare(List.of(1L, 2L, 3L));

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
    ProductComparisonQueryRepository repository = mock(ProductComparisonQueryRepository.class);
    ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);
    stubFacts(repository, 1L);
    stubFacts(repository, 2L);
    when(ai.compare(anyList())).thenReturn("질병 치료를 보장합니다.");

    ProductComparisonResponse response =
        new ProductComparisonService(repository, ai).compare(List.of(1L, 2L));

    assertThat(response.aiStatus()).isEqualTo("UNAVAILABLE");
    assertThat(response.aiSummary()).isNull();
  }

  @Test
  void aiFailureLeavesCanonicalFactsAvailable() {
    ProductComparisonQueryRepository repository = mock(ProductComparisonQueryRepository.class);
    ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);
    ProductComparisonQueryRepository.RawFacts row =
        new ProductComparisonQueryRepository.RawFacts(
            1L, "상품", "thumb", "브랜드", "사료", new BigDecimal("1000.00"),
            new BigDecimal("1200.00"), new BigDecimal("4.50"), 3L, true, true);
    when(repository.findFacts(1L)).thenReturn(Optional.of(row));
    when(repository.findFacts(2L)).thenReturn(Optional.of(row));
    when(repository.findFacets(1L)).thenReturn(List.of("size:small"));
    when(repository.findFacets(2L)).thenReturn(List.of("size:small"));
    when(ai.compare(anyList())).thenThrow(new IllegalStateException("provider unavailable"));

    ProductComparisonResponse response =
        new ProductComparisonService(repository, ai).compare(List.of(1L, 2L));

    assertThat(response.aiStatus()).isEqualTo("UNAVAILABLE");
    assertThat(response.aiSummary()).isNull();
    assertThat(response.products()).hasSize(2);
    assertThat(response.products().getFirst().representativePrice())
        .isEqualByComparingTo("1000.00");
    assertThat(response.products().getFirst().purchasable()).isTrue();
  }

  private void stubFacts(ProductComparisonQueryRepository repository, long id) {
    when(repository.findFacts(eq(id)))
        .thenReturn(
            Optional.of(
                new ProductComparisonQueryRepository.RawFacts(
                    id,
                    "상품" + id,
                    "thumb",
                    "브랜드",
                    "사료",
                    new BigDecimal("1000.00"),
                    new BigDecimal("1200.00"),
                    new BigDecimal("4.50"),
                    3L,
                    true,
                    true)));
    when(repository.findFacets(eq(id))).thenReturn(List.of("size:small"));
  }
}
