package com.pawcycle.backend.subscription.v2;

import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class V2IdempotencyCleanupService {
	private static final Duration RETENTION = Duration.ofDays(30);
	private final JdbcTemplate jdbc;
	private final Clock clock;
	private final V2SubscriptionMetrics metrics;

	public V2IdempotencyCleanupService(JdbcTemplate jdbc, Clock clock, V2SubscriptionMetrics metrics) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.metrics = metrics;
	}

	@Transactional
	public CleanupResult deleteExpired(int batchSize) {
		if (batchSize <= 0) {
			throw new IllegalArgumentException("batchSize must be positive");
		}

		Timer.Sample sample = metrics.startCleanup();
		AtomicReference<CleanupResult> completedResult = new AtomicReference<>();
		boolean transactionSynchronized = TransactionSynchronizationManager.isSynchronizationActive();
		if (transactionSynchronized) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					CleanupResult result = completedResult.get();
					if (status == STATUS_COMMITTED && result != null) {
						metrics.cleanupSucceeded(result);
					} else {
						metrics.cleanupFailed();
					}
					metrics.finishCleanup(sample);
				}
			});
		}
		try {
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
			CleanupResult result = new CleanupResult(
					creationRepaired, commandRepaired, creationDeleted, commandDeleted);
			completedResult.set(result);
			if (!transactionSynchronized) {
				metrics.cleanupSucceeded(result);
			}
			return result;
		} catch (RuntimeException exception) {
			if (!transactionSynchronized) {
				metrics.cleanupFailed();
			}
			throw exception;
		} finally {
			if (!transactionSynchronized) {
				metrics.finishCleanup(sample);
			}
		}
	}

	public record CleanupResult(
			int creationRepaired,
			int commandRepaired,
			int creationDeleted,
			int commandDeleted) {}
}
