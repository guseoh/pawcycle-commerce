package com.pawcycle.backend.subscription.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class V2SubscriptionMetricsTests {

	@Test
	void gaugeReadUsesCachedSnapshotWithoutJdbcQuery() {
		JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		new V2SubscriptionMetrics(registry, jdbc, Clock.systemUTC());

		assertThat(registry.get("pawcycle.subscription.idempotency.retained.rows")
				.tag("scope", "creation").gauge().value()).isNaN();
		assertThat(registry.get("pawcycle.subscription.idempotency.cleanup.candidates")
				.tag("scope", "command").gauge().value()).isNaN();
		assertThat(registry.get("pawcycle.subscription.idempotency.metrics.refresh.last.success")
				.gauge().value()).isNaN();
		assertThat(registry.get("pawcycle.subscription.idempotency.metrics.refresh.age.seconds")
				.gauge().value()).isNaN();

		verifyNoInteractions(jdbc);
	}

	@Test
	void gaugeRefreshUpdatesSnapshotAndPreservesLastSuccessOnFailure() {
		JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
		Instant now = Instant.parse("2026-08-09T00:00:00Z");
		Clock clock = Clock.fixed(now, ZoneOffset.UTC);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		V2SubscriptionMetrics metrics = new V2SubscriptionMetrics(registry, jdbc, clock);
		when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(11L, 22L);
		when(jdbc.queryForObject(anyString(), eq(Long.class), any(LocalDateTime.class)))
				.thenReturn(3L, 4L);

		metrics.refreshIdempotencyGauges();

		assertThat(registry.get("pawcycle.subscription.idempotency.retained.rows")
				.tag("scope", "creation").gauge().value()).isEqualTo(11);
		assertThat(registry.get("pawcycle.subscription.idempotency.cleanup.candidates")
				.tag("scope", "creation").gauge().value()).isEqualTo(3);
		assertThat(registry.get("pawcycle.subscription.idempotency.retained.rows")
				.tag("scope", "command").gauge().value()).isEqualTo(22);
		assertThat(registry.get("pawcycle.subscription.idempotency.cleanup.candidates")
				.tag("scope", "command").gauge().value()).isEqualTo(4);
		assertThat(registry.get("pawcycle.subscription.idempotency.metrics.refresh.failures")
				.counter().count()).isZero();
		assertThat(registry.get("pawcycle.subscription.idempotency.metrics.refresh.last.success")
				.gauge().value()).isEqualTo((double) now.getEpochSecond());
		assertThat(registry.get("pawcycle.subscription.idempotency.metrics.refresh.age.seconds")
				.gauge().value()).isZero();

		when(jdbc.queryForObject(anyString(), eq(Long.class)))
				.thenThrow(new IllegalStateException("test refresh failure"));

		metrics.refreshIdempotencyGauges();

		assertThat(registry.get("pawcycle.subscription.idempotency.retained.rows")
				.tag("scope", "creation").gauge().value()).isEqualTo(11);
		assertThat(registry.get("pawcycle.subscription.idempotency.cleanup.candidates")
				.tag("scope", "creation").gauge().value()).isEqualTo(3);
		assertThat(registry.get("pawcycle.subscription.idempotency.retained.rows")
				.tag("scope", "command").gauge().value()).isEqualTo(22);
		assertThat(registry.get("pawcycle.subscription.idempotency.cleanup.candidates")
				.tag("scope", "command").gauge().value()).isEqualTo(4);
		assertThat(registry.get("pawcycle.subscription.idempotency.metrics.refresh.failures")
				.counter().count()).isEqualTo(1);
		assertThat(registry.get("pawcycle.subscription.idempotency.metrics.refresh.last.success")
				.gauge().value()).isEqualTo((double) now.getEpochSecond());
		assertThat(registry.get("pawcycle.subscription.idempotency.metrics.refresh.age.seconds")
				.gauge().value()).isZero();
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
