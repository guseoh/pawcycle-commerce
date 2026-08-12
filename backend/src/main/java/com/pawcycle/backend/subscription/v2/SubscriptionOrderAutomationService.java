package com.pawcycle.backend.subscription.v2;

import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
			      FROM subscription_schedules prior_schedule
			      JOIN subscription_order_context prior_context ON prior_context.schedule_id = prior_schedule.id
			      JOIN payments prior_payment ON prior_payment.order_id = prior_context.order_id
			      WHERE prior_schedule.subscription_id = schedule.subscription_id
			        AND (prior_schedule.scheduled_date < schedule.scheduled_date
			             OR (prior_schedule.scheduled_date = schedule.scheduled_date AND prior_schedule.id < schedule.id))
			        AND prior_payment.status <> 'SUCCEEDED'
			  )
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
		Map<String, Object> shipping = shippingSnapshotOrDefault(
				candidate.subscriptionId(), longValue(subscription, "member_id"));
		if (shipping == null) {
			hold(candidate.scheduleId(), "MISSING_SHIPPING_ADDRESS");
			return ProcessingOutcome.NO_OP;
		}
		if (one("SELECT id FROM billing_payment_methods WHERE member_id=? AND status='ACTIVE' FOR UPDATE",
				longValue(subscription, "member_id")).isEmpty()) {
			hold(candidate.scheduleId(), "MISSING_BILLING_METHOD");
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
		} else {
			effectiveSnapshotId = currentSnapshotId;
		}
		Map<String, Object> effectiveSnapshot = snapshot(candidate.subscriptionId(), effectiveSnapshotId);
		int deliveryCycleWeeks = intValue(currentSnapshot, "delivery_cycle_weeks");
		if (intValue(effectiveSnapshot, "delivery_cycle_weeks") != deliveryCycleWeeks) {
			throw new IllegalStateException("Pending snapshot changed the existing delivery cycle");
		}

		List<Map<String, Object>> items = jdbc.queryForList(
				"SELECT sku_id,quantity FROM subscription_snapshot_items "
						+ "WHERE snapshot_id=? ORDER BY sku_id",
				effectiveSnapshotId);
		if (items.isEmpty()) {
			throw new IllegalStateException("Effective snapshot has no order items");
		}

		createCommonOrder(subscription, schedule, effectiveSnapshot, shipping, items);
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

	private Map<String, Object> shippingSnapshotOrDefault(long subscriptionId, long memberId) {
		Optional<Map<String, Object>> existing = one(
				"SELECT recipient_name,recipient_phone,postal_code,address_line1,address_line2 "
						+ "FROM subscription_shipping_snapshots WHERE subscription_id=? FOR UPDATE",
				subscriptionId);
		if (existing.isPresent()) return existing.get();
		Optional<Map<String, Object>> defaultAddress = one("""
				SELECT address.recipient_name,address.recipient_phone,address.postal_code,address.address_line1,address.address_line2
				FROM members member JOIN member_addresses address ON address.id=member.default_address_id
				WHERE member.id=? FOR UPDATE""", memberId);
		if (defaultAddress.isEmpty()) return null;
		Map<String, Object> address = defaultAddress.get();
		jdbc.update("""
				INSERT INTO subscription_shipping_snapshots(subscription_id,recipient_name,recipient_phone,postal_code,address_line1,address_line2,updated_at)
				VALUES (?,?,?,?,?,?,?)""", subscriptionId, address.get("recipient_name"), address.get("recipient_phone"),
				address.get("postal_code"), address.get("address_line1"), address.get("address_line2"),
				LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
		return address;
	}

	private long createCommonOrder(
			Map<String, Object> subscription,
			Map<String, Object> schedule,
			Map<String, Object> effectiveSnapshot,
			Map<String, Object> shipping,
			List<Map<String, Object>> snapshotItems) {
		BigDecimal total = BigDecimal.valueOf(longValue(effectiveSnapshot, "package_total_krw"));
		String orderNumber = "SUB-" + UUID.randomUUID();
		LocalDateTime timestamp = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		jdbc.update("""
				INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,
				 recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at)
				VALUES (?,?,'SUBSCRIPTION','PAYMENT_PENDING',?,0,0,?,?,?,?,?,?,?)""",
				orderNumber, longValue(subscription, "member_id"), total, total, shipping.get("recipient_name"),
				shipping.get("recipient_phone"), shipping.get("postal_code"), shipping.get("address_line1"),
				shipping.get("address_line2"), timestamp);
		long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("""
				INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,effective_snapshot_id,source_plan_version_id,scheduled_date)
				VALUES (?,?,?,?,?,?)""", orderId, longValue(subscription, "id"), longValue(schedule, "id"),
				longValue(effectiveSnapshot, "id"), longValue(effectiveSnapshot, "source_plan_version_id"),
				dateValue(schedule, "scheduled_date"));
		String providerOrderId = "TOSS-SUB-" + UUID.randomUUID();
		jdbc.update("""
				INSERT INTO payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,attempt_no,requested_at,created_at)
				VALUES (?,'BILLING','TOSS','READY',?,?,?,?,?,?)""", orderId, total, providerOrderId,
				"billing-" + UUID.randomUUID(), 1, timestamp, timestamp);
		long paymentId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		List<Map<String, Object>> items = jdbc.queryForList("""
				SELECT item.sku_id,item.quantity,sku.sku_code,sku.name AS sku_name,sku.price,product.name AS product_name
				FROM subscription_snapshot_items item JOIN skus sku ON sku.id=item.sku_id
				JOIN products product ON product.id=sku.product_id WHERE item.snapshot_id=? ORDER BY item.sku_id""",
				longValue(effectiveSnapshot, "id"));
		if (items.size() != snapshotItems.size()) throw new IllegalStateException("Subscription snapshot SKU is missing");
		for (Map<String, Object> item : items) {
			int quantity = intValue(item, "quantity");
			reserveInventory(longValue(item, "sku_id"), quantity, paymentId, timestamp);
			BigDecimal unitPrice = (BigDecimal) item.get("price");
			jdbc.update("""
					INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount)
					VALUES (?,?,'FULL',?,?,?,?,?,?)""", orderId, longValue(item, "sku_id"), item.get("sku_code"),
					item.get("product_name"), item.get("sku_name"), unitPrice, quantity, unitPrice.multiply(BigDecimal.valueOf(quantity)));
		}
		return orderId;
	}

	private void reserveInventory(long skuId, int quantity, long paymentId, LocalDateTime timestamp) {
		Map<String, Object> inventory = one("SELECT available_quantity,reserved_quantity,version FROM inventories WHERE sku_id=?", skuId)
				.orElseThrow(() -> new IllegalStateException("Inventory is missing"));
		long available = longValue(inventory, "available_quantity");
		long reserved = longValue(inventory, "reserved_quantity");
		long version = longValue(inventory, "version");
		if (available < quantity || jdbc.update("""
				UPDATE inventories SET available_quantity=available_quantity-?,reserved_quantity=reserved_quantity+?,version=version+1
				WHERE sku_id=? AND version=? AND available_quantity>=?""", quantity, quantity, skuId, version, quantity) != 1) {
			throw new IllegalStateException("Inventory reservation conflict");
		}
		jdbc.update("""
				INSERT INTO inventory_movements(sku_id,payment_id,type,quantity,available_before,available_after,reserved_before,reserved_after,created_at)
				VALUES (?,?,'RESERVE',?,?,?,?,?,?)""", skuId, paymentId, quantity, available, available - quantity,
				reserved, reserved + quantity, timestamp);
	}

	private void hold(long scheduleId, String reason) {
		jdbc.update("UPDATE subscription_schedules SET status='HELD',hold_reason=? WHERE id=? AND status='SCHEDULED'", reason, scheduleId);
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
