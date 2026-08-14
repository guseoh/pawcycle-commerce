package com.pawcycle.backend.subscription.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class V2SubscriptionMetricsTests {

	@Test
	void gaugeReadUsesCachedSnapshotWithoutJdbcQuery() {
		JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		V2SubscriptionMetrics metrics = new V2SubscriptionMetrics(registry, jdbc, Clock.systemUTC());

		assertThat(registry.get("pawcycle.subscription.idempotency.retained.rows")
				.tag("scope", "creation").gauge().value()).isNaN();
		assertThat(registry.get("pawcycle.subscription.idempotency.cleanup.candidates")
				.tag("scope", "command").gauge().value()).isNaN();

		verifyNoInteractions(jdbc);
	}

	@Test
	void cleanupFailureRecordsFailureAndDurationWithoutSuccess() {
		JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
		Clock clock = Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		V2SubscriptionMetrics metrics = new V2SubscriptionMetrics(registry, jdbc, clock);
		V2IdempotencyCleanupService cleanup = new V2IdempotencyCleanupService(jdbc, clock, metrics);
		when(jdbc.update(anyString(), any(Object[].class)))
				.thenThrow(new IllegalStateException("test cleanup failure"));

		assertThatThrownBy(() -> cleanup.deleteExpired(1))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("test cleanup failure");

		assertThat(registry.get("pawcycle.subscription.idempotency.cleanup.executions")
				.tag("result", "success").counter().count()).isZero();
		assertThat(registry.get("pawcycle.subscription.idempotency.cleanup.executions")
				.tag("result", "failure").counter().count()).isEqualTo(1);
		assertThat(registry.get("pawcycle.subscription.idempotency.cleanup.duration").timer().count()).isEqualTo(1);
	}
}
