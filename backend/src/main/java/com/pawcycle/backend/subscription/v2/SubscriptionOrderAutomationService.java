package com.pawcycle.backend.subscription.v2;

import io.micrometer.core.instrument.Timer;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SubscriptionOrderAutomationService {

	static final String UPDATE_SCHEDULE_EFFECTIVE_SQL =
			"UPDATE subscription_schedules SET effective_snapshot_id=? WHERE id=?";

	private static final Logger log = LoggerFactory.getLogger(SubscriptionOrderAutomationService.class);
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final String FIND_DUE_CANDIDATES_SQL = """
			SELECT schedule.id AS schedule_id, schedule.subscription_id
			FROM subscription_schedules schedule
			JOIN subscriptions subscription ON subscription.id = schedule.subscription_id
			LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id = schedule.id
			WHERE subscription.mvp2_managed = true
			  AND subscription.status = 'ACTIVE'
			  AND schedule.status = 'SCHEDULED'
			  AND schedule.scheduled_date <= ?
			  AND existing_order.id IS NULL
			  AND NOT EXISTS (
			      SELECT 1
			      FROM subscription_schedules earlier
			      LEFT JOIN subscription_orders earlier_order ON earlier_order.schedule_id = earlier.id
			      WHERE earlier.subscription_id = schedule.subscription_id
			        AND earlier.status = 'SCHEDULED'
			        AND earlier.scheduled_date <= ?
			        AND earlier_order.id IS NULL
			        AND (earlier.scheduled_date < schedule.scheduled_date
			             OR (earlier.scheduled_date = schedule.scheduled_date AND earlier.id < schedule.id))
			  )
			ORDER BY schedule.scheduled_date, schedule.id
			LIMIT ?
			""";

	private final JdbcTemplate jdbc;
	private final Clock clock;
	private final SubscriptionOrderAutomationMetrics metrics;
	private final TransactionTemplate targetTransaction;

	public SubscriptionOrderAutomationService(
			JdbcTemplate jdbc,
			Clock clock,
			SubscriptionOrderAutomationMetrics metrics,
			PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.metrics = metrics;
		this.targetTransaction = new TransactionTemplate(transactionManager);
		this.targetTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public BatchResult processDueSchedules(int batchSize) {
		if (batchSize < 1) {
			throw new IllegalArgumentException("batchSize must be positive");
		}

		Timer.Sample sample = metrics.start();
		int processed = 0;
		int created = 0;
		int failures = 0;
		int duplicateOrNoOp = 0;
		try {
			LocalDate today = today();
			List<Candidate> candidates = jdbc.query(
					FIND_DUE_CANDIDATES_SQL,
					(rs, rowNumber) -> new Candidate(rs.getLong("subscription_id"), rs.getLong("schedule_id")),
					today,
					today,
					batchSize);
			for (Candidate candidate : candidates) {
				processed++;
				try {
					ProcessingOutcome outcome = targetTransaction.execute(
							status -> processCandidate(candidate, today));
					if (outcome == ProcessingOutcome.CREATED) {
						created++;
					} else {
						duplicateOrNoOp++;
					}
				} catch (RuntimeException exception) {
					failures++;
					log.error(
							"Subscription order automation failed; subscriptionId={}, scheduleId={}, failureCategory={}",
							candidate.subscriptionId(),
							candidate.scheduleId(),
							failureCategory(exception));
				}
			}
			return new BatchResult(processed, created, failures, duplicateOrNoOp);
		} catch (RuntimeException exception) {
			failures++;
			log.error(
					"Subscription order automation batch failed; failureCategory={}",
					failureCategory(exception));
			throw exception;
		} finally {
			metrics.finish(sample, processed, created, failures, duplicateOrNoOp);
		}
	}

	private ProcessingOutcome processCandidate(Candidate candidate, LocalDate today) {
		Optional<Map<String, Object>> maybeSubscription = one(
				"SELECT id,member_id,status,mvp2_managed,version,current_snapshot_id "
						+ "FROM subscriptions WHERE id=? FOR UPDATE",
				candidate.subscriptionId());
		if (maybeSubscription.isEmpty()) {
			return ProcessingOutcome.NO_OP;
		}
		Map<String, Object> subscription = maybeSubscription.get();
		if (!Boolean.TRUE.equals(subscription.get("mvp2_managed"))
				|| !"ACTIVE".equals(subscription.get("status"))) {
			return ProcessingOutcome.NO_OP;
		}

		Optional<Map<String, Object>> maybeSchedule = one(
				"SELECT id,subscription_id,scheduled_date,status,effective_snapshot_id "
						+ "FROM subscription_schedules WHERE id=? FOR UPDATE",
				candidate.scheduleId());
		if (maybeSchedule.isEmpty()) {
			return ProcessingOutcome.NO_OP;
		}
		Map<String, Object> schedule = maybeSchedule.get();
		LocalDate scheduledDate = dateValue(schedule, "scheduled_date");
		if (longValue(schedule, "subscription_id") != candidate.subscriptionId()
				|| !"SCHEDULED".equals(schedule.get("status"))
				|| scheduledDate.isAfter(today)) {
			return ProcessingOutcome.NO_OP;
		}
		if (orderExists(candidate.scheduleId())) {
			return ProcessingOutcome.NO_OP;
		}

		long currentSnapshotId = longValue(subscription, "current_snapshot_id");
		Map<String, Object> currentSnapshot = snapshot(candidate.subscriptionId(), currentSnapshotId);
		Optional<Map<String, Object>> pending = one(
				"SELECT snapshot_id,target_schedule_id FROM pending_plan_changes "
						+ "WHERE subscription_id=? FOR UPDATE",
				candidate.subscriptionId());
		boolean appliesPending = pending
				.map(row -> longValue(row, "target_schedule_id") == candidate.scheduleId())
				.orElse(false);
		long effectiveSnapshotId;
		if (appliesPending) {
			effectiveSnapshotId = longValue(pending.orElseThrow(), "snapshot_id");
		} else if (schedule.get("effective_snapshot_id") != null) {
			effectiveSnapshotId = longValue(schedule, "effective_snapshot_id");
		} else {
			effectiveSnapshotId = currentSnapshotId;
		}
		Map<String, Object> effectiveSnapshot = snapshot(candidate.subscriptionId(), effectiveSnapshotId);
		int deliveryCycleWeeks = intValue(currentSnapshot, "delivery_cycle_weeks");
		if (intValue(effectiveSnapshot, "delivery_cycle_weeks") != deliveryCycleWeeks) {
			throw new IllegalStateException("Pending snapshot changed the existing delivery cycle");
		}
		if (schedule.get("effective_snapshot_id") != null
				&& longValue(schedule, "effective_snapshot_id") != effectiveSnapshotId) {
			throw new IllegalStateException("Schedule effective snapshot conflicts with the selected snapshot");
		}

		List<Map<String, Object>> items = jdbc.queryForList(
				"SELECT sku_id,quantity FROM subscription_snapshot_items "
						+ "WHERE snapshot_id=? ORDER BY sku_id",
				effectiveSnapshotId);
		if (items.isEmpty()) {
			throw new IllegalStateException("Effective snapshot has no order items");
		}

		long orderId;
		try {
			jdbc.update(
					"INSERT INTO subscription_orders("
							+ "member_id,subscription_id,schedule_id,effective_snapshot_id,"
							+ "source_plan_version_id,scheduled_date,processed_at,package_total_krw,status"
							+ ") VALUES (?,?,?,?,?,?,?,?,'CREATED')",
					longValue(subscription, "member_id"),
					candidate.subscriptionId(),
					candidate.scheduleId(),
					effectiveSnapshotId,
					longValue(effectiveSnapshot, "source_plan_version_id"),
					scheduledDate,
					LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
					longValue(effectiveSnapshot, "package_total_krw"));
			orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		} catch (DuplicateKeyException duplicate) {
			if (orderExists(candidate.scheduleId())) {
				return ProcessingOutcome.NO_OP;
			}
			throw duplicate;
		}
		for (Map<String, Object> item : items) {
			jdbc.update(
					"INSERT INTO subscription_order_items(order_id,sku_id,quantity) VALUES (?,?,?)",
					orderId,
					longValue(item, "sku_id"),
					intValue(item, "quantity"));
		}

		jdbc.update(UPDATE_SCHEDULE_EFFECTIVE_SQL, effectiveSnapshotId, candidate.scheduleId());
		if (appliesPending) {
			int promoted = jdbc.update(
					"UPDATE subscriptions SET current_snapshot_id=? "
							+ "WHERE id=? AND current_snapshot_id=?",
					effectiveSnapshotId,
					candidate.subscriptionId(),
					currentSnapshotId);
			int removed = jdbc.update(
					"DELETE FROM pending_plan_changes "
							+ "WHERE subscription_id=? AND snapshot_id=? AND target_schedule_id=?",
					candidate.subscriptionId(),
					effectiveSnapshotId,
					candidate.scheduleId());
			if (promoted != 1 || removed != 1) {
				throw new IllegalStateException("Pending snapshot promotion lost its target");
			}
		}

		LocalDate nextScheduledDate = firstFutureDate(scheduledDate, deliveryCycleWeeks, today);
		List<Map<String, Object>> futureSchedules = jdbc.queryForList(
				"SELECT id,scheduled_date FROM subscription_schedules "
						+ "WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>? "
						+ "ORDER BY scheduled_date,id FOR UPDATE",
				candidate.subscriptionId(),
				today);
		if (futureSchedules.isEmpty()) {
			jdbc.update(
					"INSERT INTO subscription_schedules("
							+ "subscription_id,scheduled_date,status,effective_snapshot_id"
							+ ") VALUES (?,?,'SCHEDULED',NULL)",
					candidate.subscriptionId(),
					nextScheduledDate);
		} else if (futureSchedules.size() != 1
				|| !nextScheduledDate.equals(dateValue(futureSchedules.getFirst(), "scheduled_date"))) {
			throw new IllegalStateException("Future Schedule cardinality is not safely recoverable");
		}

		long expectedVersion = longValue(subscription, "version");
		int versionUpdated = jdbc.update(
				"UPDATE subscriptions SET version=version+1 WHERE id=? AND version=?",
				candidate.subscriptionId(),
				expectedVersion);
		if (versionUpdated != 1) {
			throw new IllegalStateException("Subscription version changed while locked");
		}
		return ProcessingOutcome.CREATED;
	}

	static LocalDate firstFutureDate(LocalDate scheduledDate, int deliveryCycleWeeks, LocalDate today) {
		LocalDate next = scheduledDate;
		do {
			next = next.plusWeeks(deliveryCycleWeeks);
		} while (!next.isAfter(today));
		return next;
	}

	private Map<String, Object> snapshot(long subscriptionId, long snapshotId) {
		return one(
				"SELECT id,source_plan_version_id,package_total_krw,delivery_cycle_weeks "
						+ "FROM subscription_snapshots WHERE id=? AND subscription_id=?",
				snapshotId,
				subscriptionId)
				.orElseThrow(() -> new IllegalStateException("Subscription snapshot is missing"));
	}

	private boolean orderExists(long scheduleId) {
		return !jdbc.queryForList(
				"SELECT id FROM subscription_orders WHERE schedule_id=? FOR UPDATE",
				scheduleId).isEmpty();
	}

	private Optional<Map<String, Object>> one(String sql, Object... arguments) {
		List<Map<String, Object>> rows = jdbc.queryForList(sql, arguments);
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
	}

	private LocalDate today() {
		return LocalDate.ofInstant(clock.instant(), SEOUL);
	}

	private static LocalDate dateValue(Map<String, Object> row, String key) {
		return ((Date) row.get(key)).toLocalDate();
	}

	private static long longValue(Map<String, Object> row, String key) {
		return ((Number) row.get(key)).longValue();
	}

	private static int intValue(Map<String, Object> row, String key) {
		return ((Number) row.get(key)).intValue();
	}

	private static String failureCategory(RuntimeException exception) {
		if (exception instanceof DataIntegrityViolationException) {
			return "DATA_INTEGRITY";
		}
		if (exception instanceof DataAccessException) {
			return "DATABASE";
		}
		if (exception instanceof IllegalStateException) {
			return "INVARIANT";
		}
		return "UNEXPECTED";
	}

	private enum ProcessingOutcome {
		CREATED,
		NO_OP
	}

	private record Candidate(long subscriptionId, long scheduleId) {}

	public record BatchResult(int processedCandidates, int ordersCreated, int failures, int duplicateOrNoOp) {}
}
