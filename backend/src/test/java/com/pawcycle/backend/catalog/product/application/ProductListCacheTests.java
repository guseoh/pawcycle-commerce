package com.pawcycle.backend.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class ProductListCacheTests {
	private StringRedisTemplate redisTemplate;
	private ValueOperations<String, String> valueOperations;
	private SimpleMeterRegistry meterRegistry;
	private ProductListCache cache;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		meterRegistry = new SimpleMeterRegistry();
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		cache = new ProductListCache(
				redisTemplate, new ObjectMapper(), meterRegistry, Duration.ofMinutes(5), true);
	}

	@Test
	void firstReadMissesAndSubsequentReadHitsWithoutReloading() throws Exception {
		ProductListView expected = productListView();
		String json = new ObjectMapper().writeValueAsString(expected);
		when(valueOperations.get(ProductListCache.CACHE_KEY)).thenReturn(null, json);
		AtomicInteger loads = new AtomicInteger();

		ProductListView first = cache.getOrLoad(() -> {
			loads.incrementAndGet();
			return expected;
		});
		ProductListView second = cache.getOrLoad(() -> {
			loads.incrementAndGet();
			return expected;
		});

		assertThat(first).isEqualTo(expected);
		assertThat(second).isEqualTo(expected);
		assertThat(loads).hasValue(1);
		verify(valueOperations).set(ProductListCache.CACHE_KEY, json, Duration.ofMinutes(5));
		assertThat(counter("hit")).isEqualTo(1.0);
		assertThat(counter("miss")).isEqualTo(1.0);
		assertThat(counter("error")).isZero();
	}

	@Test
	void redisUnavailableFallsBackToAuthoritativeLoader() {
		ProductListView expected = productListView();
		when(valueOperations.get(ProductListCache.CACHE_KEY))
				.thenThrow(new IllegalStateException("redis unavailable"));

		assertThat(cache.getOrLoad(() -> expected)).isEqualTo(expected);
		verify(valueOperations, never()).set(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(Duration.class));
		assertThat(counter("error")).isEqualTo(1.0);
	}

	@Test
	void databaseFailureIsNotHiddenAfterRedisFailure() {
		when(valueOperations.get(ProductListCache.CACHE_KEY))
				.thenThrow(new IllegalStateException("redis unavailable"));

		assertThatThrownBy(() -> cache.getOrLoad(() -> {
			throw new IllegalArgumentException("database unavailable");
		})).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("database unavailable");
	}

	@Test
	void invalidationDeletesOnlyTheVersionedListKey() {
		cache.invalidate();

		verify(redisTemplate).delete("pawcycle:catalog:product-list:v1");
	}

	@Test
	void disabledCacheUsesLoaderWithoutRedisAccess() {
		clearInvocations(redisTemplate);
		ProductListCache disabled = new ProductListCache(
				redisTemplate, new ObjectMapper(), meterRegistry, Duration.ofMinutes(5), false);
		ProductListView expected = new ProductListView(List.of());

		assertThat(disabled.getOrLoad(() -> expected)).isSameAs(expected);
		verify(redisTemplate, never()).opsForValue();
	}

	private double counter(String result) {
		return meterRegistry.get("pawcycle.catalog.product.list.cache.operations")
				.tag("result", result)
				.counter()
				.count();
	}

	private ProductListView productListView() {
		return new ProductListView(List.of(new ProductListView.ProductSummary(
				2L,
				"둘째 상품",
				"CAT",
				"설명",
				null,
				new ProductListView.SkuPriceSummary(List.of(
						new ProductListView.SkuPrice(20L, "2kg", new BigDecimal("19900.00")))),
				true)));
	}
}
