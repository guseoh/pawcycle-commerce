package com.pawcycle.backend.catalog.product.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProductListCache {
	static final String CACHE_KEY = "pawcycle:catalog:product-list:v1";
	private static final String METRIC_NAME = "pawcycle.catalog.product.list.cache.operations";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final Duration ttl;
	private final boolean enabled;
	private final Counter hitCounter;
	private final Counter missCounter;
	private final Counter errorCounter;

	public ProductListCache(
			StringRedisTemplate redisTemplate,
			ObjectMapper objectMapper,
			MeterRegistry meterRegistry,
			@Value("${pawcycle.catalog.product-list-cache.ttl:PT5M}") Duration ttl,
			@Value("${pawcycle.catalog.product-list-cache.enabled:false}") boolean enabled) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		if (ttl.isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("product list cache ttl must be positive");
		}
		this.ttl = ttl;
		this.enabled = enabled;
		this.hitCounter = counter(meterRegistry, "hit");
		this.missCounter = counter(meterRegistry, "miss");
		this.errorCounter = counter(meterRegistry, "error");
	}

	public ProductListView getOrLoad(Supplier<ProductListView> loader) {
		if (!enabled) {
			return loader.get();
		}

		String cached;
		try {
			cached = redisTemplate.opsForValue().get(CACHE_KEY);
			if (cached != null) {
				ProductListView value = objectMapper.readValue(cached, ProductListView.class);
				hitCounter.increment();
				return value;
			}
			missCounter.increment();
		} catch (RuntimeException exception) {
			errorCounter.increment();
			return loader.get();
		}

		ProductListView value = loader.get();
		try {
			redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(value), ttl);
		} catch (RuntimeException exception) {
			errorCounter.increment();
		}
		return value;
	}

	public void invalidate() {
		if (!enabled) {
			return;
		}
		try {
			redisTemplate.delete(CACHE_KEY);
		} catch (RuntimeException exception) {
			errorCounter.increment();
		}
	}

	private Counter counter(MeterRegistry meterRegistry, String result) {
		return Counter.builder(METRIC_NAME)
				.description("Product list cache operation outcomes")
				.tag("result", result)
				.register(meterRegistry);
	}
}
