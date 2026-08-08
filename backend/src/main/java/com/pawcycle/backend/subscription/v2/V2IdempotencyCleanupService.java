package com.pawcycle.backend.subscription.v2;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class V2IdempotencyCleanupService {
	private static final Duration RETENTION = Duration.ofDays(30);
	private final JdbcTemplate jdbc;
	private final Clock clock;

	public V2IdempotencyCleanupService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional
	public CleanupResult deleteExpired(int batchSize) {
		if (batchSize <= 0) {
			throw new IllegalArgumentException("batchSize must be positive");
		}

		LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant().minus(RETENTION), ZoneOffset.UTC);
		int creationDeleted = jdbc.update(
				"DELETE FROM subscription_creation_idempotency_results "
						+ "WHERE completed_at < ? ORDER BY completed_at,member_id,idempotency_key LIMIT ?",
				cutoff,
				batchSize);
		int commandDeleted = jdbc.update(
				"DELETE FROM subscription_command_idempotency_results "
						+ "WHERE completed_at < ? ORDER BY completed_at,member_id,subscription_id,command_type,idempotency_key LIMIT ?",
				cutoff,
				batchSize);
		return new CleanupResult(creationDeleted, commandDeleted);
	}

	public record CleanupResult(int creationDeleted, int commandDeleted) {}
}
