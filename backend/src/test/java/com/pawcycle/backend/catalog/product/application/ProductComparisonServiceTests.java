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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ProductComparisonServiceTests {
	@Test
	void invalidProductCountIsRejectedBeforeCanonicalQueries() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ProductComparisonService(jdbc, ai).compare(List.of(1L)))
				.isInstanceOf(ProductComparisonException.class)
				.hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
		verifyNoInteractions(jdbc, ai);
	}

	@Test
	void aiFailureLeavesCanonicalFactsAvailable() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		ProductComparisonAiClient ai = mock(ProductComparisonAiClient.class);
		Map<String, Object> row = Map.ofEntries(
				Map.entry("id", 1L), Map.entry("name", "상품"), Map.entry("thumbnail_url", "thumb"),
				Map.entry("brand_name", "브랜드"), Map.entry("category_name", "사료"),
				Map.entry("price", new BigDecimal("1000.00")), Map.entry("compare_at_price", new BigDecimal("1200.00")),
				Map.entry("average_rating", new BigDecimal("4.50")), Map.entry("review_count", 3L),
				Map.entry("subscription_eligible", true), Map.entry("purchasable", true));
		when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(row));
		when(jdbc.queryForList(anyString(), eq(2L))).thenReturn(List.of(row));
		when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(1L)))
				.thenReturn(List.of("size:small"));
		when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(2L)))
				.thenReturn(List.of("size:small"));
		when(ai.compare(anyList())).thenThrow(new IllegalStateException("provider unavailable"));

		ProductComparisonService.ComparisonResponse response = new ProductComparisonService(jdbc, ai).compare(List.of(1L, 2L));

		assertThat(response.aiStatus()).isEqualTo("UNAVAILABLE");
		assertThat(response.aiSummary()).isNull();
		assertThat(response.products()).hasSize(2);
		assertThat(response.products().getFirst().representativePrice()).isEqualByComparingTo("1000.00");
	}
}
