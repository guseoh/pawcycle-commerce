package com.pawcycle.backend.catalog.product.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProductListCache {
	static final String CACHE_KEY = "pawcycle:catalog:product-list:v2";
	static final String GENERATION_KEY = CACHE_KEY + ":generation";
	static final DefaultRedisScript<Long> STORE_IF_GENERATION_UNCHANGED_SCRIPT = new DefaultRedisScript<>("""
			local current = redis.call('GET', KEYS[1])
			if not current then current = '0' end
			if current ~= ARGV[1] then return 0 end
			redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
			return 1
			""", Long.class);
	static final DefaultRedisScript<Long> INVALIDATE_SCRIPT = new DefaultRedisScript<>("""
			redis.call('INCR', KEYS[1])
			redis.call('DEL', KEYS[2])
			return 1
			""", Long.class);
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
		if (ttl.isZero() || ttl.isNegative() || ttl.toMillis() == 0) {
			throw new IllegalArgumentException("product list cache ttl must be at least one millisecond");
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

		String generation;
		try {
			String cached = redisTemplate.opsForValue().get(CACHE_KEY);
			if (cached != null) {
				ProductListView value = objectMapper.readValue(cached, ProductListView.class);
				hitCounter.increment();
				return value;
			}
			missCounter.increment();
			generation = redisTemplate.opsForValue().get(GENERATION_KEY);
			if (generation == null) {
				generation = "0";
			}
		} catch (RuntimeException exception) {
			errorCounter.increment();
			return loader.get();
		}

		ProductListView value = loader.get();
		try {
			String serialized = objectMapper.writeValueAsString(value);
			redisTemplate.execute(
					STORE_IF_GENERATION_UNCHANGED_SCRIPT,
					List.of(GENERATION_KEY, CACHE_KEY),
					generation,
					serialized,
					Long.toString(ttl.toMillis()));
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
			redisTemplate.execute(INVALIDATE_SCRIPT, List.of(GENERATION_KEY, CACHE_KEY));
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
