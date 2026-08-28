package com.pawcycle.backend.catalog.engagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ReviewSummaryServiceTests {
	@Test
	void fewerThanThreeVisibleReviewsDoesNotCallAi() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		ProductRepository products = mock(ProductRepository.class);
		ReviewSummaryAiClient ai = mock(ReviewSummaryAiClient.class);
		when(products.findPublicById(1L)).thenReturn(Optional.of(mock(Product.class)));
		when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("JOIN brands"), eq(Integer.class), eq(1L))).thenReturn(1);
		when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(1L)))
				.thenReturn(List.of());
		when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L))).thenReturn(2L);
		when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(1L))).thenReturn(new BigDecimal("4.50"));

		ReviewSummaryService.ReviewSummaryResponse response = new ReviewSummaryService(
				jdbc, products, ai, Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)).summary(1L);

		assertThat(response.status()).isEqualTo("INSUFFICIENT_REVIEWS");
		assertThat(response.summary()).isNull();
		assertThat(response.reviewCount()).isEqualTo(2L);
		verifyNoInteractions(ai);
	}
}
