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

		LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		int creationRepaired = jdbc.update(
				"UPDATE subscription_creation_idempotency_results SET completed_at=? "
						+ "WHERE completed_at IS NULL AND response_status BETWEEN 200 AND 299 AND response_body IS NOT NULL "
						+ "ORDER BY member_id,idempotency_key LIMIT ?",
				now,
				batchSize);
		int commandRepaired = jdbc.update(
				"UPDATE subscription_command_idempotency_results SET completed_at=? "
						+ "WHERE completed_at IS NULL AND response_status BETWEEN 200 AND 299 AND response_body IS NOT NULL "
						+ "ORDER BY member_id,subscription_id,command_type,idempotency_key LIMIT ?",
				now,
				batchSize);
		LocalDateTime cutoff = now.minus(RETENTION);
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
		return new CleanupResult(creationRepaired, commandRepaired, creationDeleted, commandDeleted);
	}

	public record CleanupResult(
			int creationRepaired,
			int commandRepaired,
			int creationDeleted,
			int commandDeleted) {}
}
