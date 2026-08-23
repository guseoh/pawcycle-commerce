package com.pawcycle.backend.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
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
		when(valueOperations.get(ProductListCache.GENERATION_KEY)).thenReturn("0");
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
		verify(redisTemplate).execute(
				eq(ProductListCache.STORE_IF_GENERATION_UNCHANGED_SCRIPT),
				eq(List.of(ProductListCache.GENERATION_KEY, ProductListCache.CACHE_KEY)),
				any(Object[].class));
		assertThat(counter("hit")).isEqualTo(1.0);
		assertThat(counter("miss")).isEqualTo(1.0);
		assertThat(counter("error")).isZero();
	}

	@Test
	void invalidationDuringLoadCannotReinsertStaleValue() {
		AtomicLong generation = new AtomicLong();
		AtomicReference<String> cachedJson = new AtomicReference<>();
		AtomicInteger loads = new AtomicInteger();
		ProductListView stale = productListView();
		ProductListView fresh = new ProductListView(List.of());

		when(valueOperations.get(ProductListCache.CACHE_KEY)).thenAnswer(ignored -> cachedJson.get());
		when(valueOperations.get(ProductListCache.GENERATION_KEY))
				.thenAnswer(ignored -> Long.toString(generation.get()));
		doAnswer(invocation -> {
			RedisScript<?> script = invocation.getArgument(0);
			Object[] scriptArguments = (Object[]) invocation.getRawArguments()[2];
			if (script == ProductListCache.INVALIDATE_SCRIPT) {
				generation.incrementAndGet();
				cachedJson.set(null);
				return 1L;
			}
			if (script == ProductListCache.STORE_IF_GENERATION_UNCHANGED_SCRIPT) {
				String expectedGeneration = (String) scriptArguments[0];
				if (!expectedGeneration.equals(Long.toString(generation.get()))) {
					return 0L;
				}
				cachedJson.set((String) scriptArguments[1]);
				return 1L;
			}
			throw new AssertionError("unexpected Redis script");
		}).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

		ProductListView first = cache.getOrLoad(() -> {
			loads.incrementAndGet();
			cache.invalidate();
			return stale;
		});

		assertThat(first).isEqualTo(stale);
		assertThat(cachedJson.get()).isNull();

		ProductListView second = cache.getOrLoad(() -> {
			loads.incrementAndGet();
			return fresh;
		});
		ProductListView third = cache.getOrLoad(() -> {
			loads.incrementAndGet();
			return stale;
		});

		assertThat(second).isEqualTo(fresh);
		assertThat(third).isEqualTo(fresh);
		assertThat(loads).hasValue(2);
	}

	@Test
	void redisUnavailableFallsBackToAuthoritativeLoader() {
		ProductListView expected = productListView();
		when(valueOperations.get(ProductListCache.CACHE_KEY))
				.thenThrow(new IllegalStateException("redis unavailable"));

		assertThat(cache.getOrLoad(() -> expected)).isEqualTo(expected);
		verify(redisTemplate, never()).execute(
				any(RedisScript.class),
				anyList(),
				any(Object[].class));
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
	void invalidationUsesAtomicGenerationAdvanceAndDelete() {
		cache.invalidate();

		verify(redisTemplate).execute(
				eq(ProductListCache.INVALIDATE_SCRIPT),
				eq(List.of(ProductListCache.GENERATION_KEY, ProductListCache.CACHE_KEY)),
				any(Object[].class));
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
