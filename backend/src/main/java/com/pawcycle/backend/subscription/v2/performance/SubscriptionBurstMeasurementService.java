package com.pawcycle.backend.subscription.v2.performance;

import com.pawcycle.backend.subscription.v2.SubscriptionOrderAutomationService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("subscription-burst-measurement & !production & !prod")
public class SubscriptionBurstMeasurementService {

	static final String FIXTURE_EMAIL_PREFIX = "perf-ph10-002-";
	static final String FIXTURE_EMAIL_SUFFIX = "@synthetic.invalid";
	static final String FIXTURE_PRODUCT_NAME = "PERF-PH10-002 synthetic product";
	static final int DEFAULT_BATCH_SIZE = 100;
	static final long DEFAULT_FIXED_DELAY_MS = 60_000L;
	private static final int MAX_COHORT_SIZE = 10_000;
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final JdbcTemplate jdbc;
	private final SubscriptionOrderAutomationService automation;
	private final Clock clock;
	private final Path workloadStartMarker;
	private final AtomicBoolean drainRunning = new AtomicBoolean();

	public SubscriptionBurstMeasurementService(
			JdbcTemplate jdbc,
			SubscriptionOrderAutomationService automation,
			Clock clock,
			@Value("${pawcycle.subscription-burst-measurement.workload-start-marker-path}")
			String workloadStartMarkerPath) {
		this.jdbc = jdbc;
		this.automation = automation;
		this.clock = clock;
		this.workloadStartMarker = Path.of(workloadStartMarkerPath).toAbsolutePath().normalize();
	}

	@Transactional
	public synchronized FixtureSummary setup(int cohortSize) {
		if (cohortSize < 1 || cohortSize > MAX_COHORT_SIZE) {
			throw new IllegalArgumentException("cohortSize must be between 1 and 10000");
		}
		if (fixtureMemberCount() != 0) {
			throw new IllegalStateException("Subscription Burst fixture already exists in this database");
		}

		LocalDate today = LocalDate.ofInstant(clock.instant(), SEOUL);
		LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		CatalogFixture catalog = createCatalogAndPlan(cohortSize);
		FixtureRows rows = jdbc.execute((ConnectionCallback<FixtureRows>) connection ->
				insertFixtureRows(connection, cohortSize, catalog, today, now));
		if (rows == null || rows.members() != cohortSize || rows.subscriptions() != cohortSize) {
			throw new IllegalStateException("Synthetic fixture cardinality is incomplete");
		}
		int backlog = dueBacklog();
		if (backlog != cohortSize || fixtureOrderCount() != 0) {
			throw new IllegalStateException("Synthetic fixture is not a clean due backlog");
		}
		return new FixtureSummary(cohortSize, backlog, DEFAULT_BATCH_SIZE, DEFAULT_FIXED_DELAY_MS);
	}

	public DrainSummary drain() {
		if (!drainRunning.compareAndSet(false, true)) {
			throw new IllegalStateException("Subscription Burst drain is already running");
		}
		try {
			int initialBacklog = dueBacklog();
			if (initialBacklog < 1) {
				throw new IllegalStateException("Synthetic due backlog is unavailable");
			}
			int expectedTicks = (initialBacklog + DEFAULT_BATCH_SIZE - 1) / DEFAULT_BATCH_SIZE;
			int safetyLimit = expectedTicks + 2;
			List<BatchMeasurement> batches = new ArrayList<>();
			int processed = 0;
			int created = 0;
			int failures = 0;
			int duplicateOrNoOp = 0;
			long drainStart = System.nanoTime();
			writeWorkloadStartMarker();
			for (int sequence = 1; sequence <= safetyLimit; sequence++) {
				long batchStart = System.nanoTime();
				SubscriptionOrderAutomationService.BatchResult result =
						automation.processDueSchedules(DEFAULT_BATCH_SIZE);
				double durationMs = elapsedMilliseconds(batchStart);
				batches.add(new BatchMeasurement(
						sequence,
						result.processedCandidates(),
						result.ordersCreated(),
						result.failures(),
						result.duplicateOrNoOp(),
						durationMs));
				processed += result.processedCandidates();
				created += result.ordersCreated();
				failures += result.failures();
				duplicateOrNoOp += result.duplicateOrNoOp();
				if (result.processedCandidates() == 0 || created >= initialBacklog) {
					break;
				}
			}
			double rawDrainElapsedMs = elapsedMilliseconds(drainStart);
			int finalBacklog = dueBacklog();
			int orderCount = fixtureOrderCount();
			int duplicateScheduleOrders = fixtureDuplicateScheduleOrderCount();
			int futureSchedules = fixtureFutureScheduleCount();
			List<Double> durations = batches.stream().map(BatchMeasurement::durationMs).sorted().toList();
			double ordersPerSecond = rawDrainElapsedMs > 0 ? created / (rawDrainElapsedMs / 1_000.0) : 0;
			long projectedCompletionMs = Math.round(rawDrainElapsedMs)
					+ Math.max(0, expectedTicks - 1) * DEFAULT_FIXED_DELAY_MS;
			boolean harnessFailure = finalBacklog != 0
					|| created != initialBacklog
					|| failures != 0
					|| duplicateOrNoOp != 0
					|| orderCount != initialBacklog
					|| duplicateScheduleOrders != 0
					|| futureSchedules != initialBacklog;
			return new DrainSummary(
					initialBacklog,
					finalBacklog,
					batches.size(),
					processed,
					created,
					failures,
					duplicateOrNoOp,
					rawDrainElapsedMs,
					ordersPerSecond,
					percentile(durations, 0.50),
					percentile(durations, 0.95),
					durations.isEmpty() ? 0 : durations.getLast(),
					DEFAULT_BATCH_SIZE,
					DEFAULT_FIXED_DELAY_MS,
					expectedTicks,
					projectedCompletionMs,
					"projection: observed raw drain duration plus 60000ms fixed delay between default scheduler ticks",
					orderCount,
					duplicateScheduleOrders,
					futureSchedules,
					harnessFailure,
					List.copyOf(batches));
		} finally {
			drainRunning.set(false);
		}
	}

	private void writeWorkloadStartMarker() {
		String startedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).toString() + "Z";
		String marker = "{\"workloadInvocationStarted\":true,\"workloadStartedAtUtc\":\""
				+ startedAt + "\"}";
		try {
			Files.writeString(
					workloadStartMarker,
					marker,
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE_NEW,
					StandardOpenOption.WRITE);
		} catch (java.io.IOException exception) {
			throw new IllegalStateException("Authoritative workload-start marker could not be recorded", exception);
		}
	}

	private CatalogFixture createCatalogAndPlan(int cohortSize) {
		jdbc.update("INSERT INTO categories(name,slug,display_order,active) VALUES ('PERF-PH10-002 synthetic','perf-ph10-002-synthetic',0,true)");
		long categoryId = lastInsertId();
		jdbc.update("INSERT INTO products(category_id,name,short_description,description,pet_type,thumbnail_url,display_status) VALUES (?,?,'synthetic measurement fixture',NULL,'DOG',NULL,'PUBLIC')", categoryId, FIXTURE_PRODUCT_NAME);
		long productId = lastInsertId();
		jdbc.update("INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES (?,'PERF-PH10-002-SKU','synthetic SKU',1000.00,true,0,'ACTIVE')", productId);
		long skuId = lastInsertId();
		jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,?,0,0)", skuId, cohortSize + 100);
		jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale,sale_starts_on,sale_ends_on,current_plan_version_id) VALUES ('PERF-PH10-002 synthetic plan','DOG',true,NULL,NULL,NULL)");
		long planId = lastInsertId();
		jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,1000,false)", planId);
		long planVersionId = lastInsertId();
		jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,1)", planVersionId, skuId);
		jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,2)", planVersionId);
		jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", planVersionId, planId);
		return new CatalogFixture(skuId, planVersionId);
	}

	private FixtureRows insertFixtureRows(
			Connection connection,
			int cohortSize,
			CatalogFixture catalog,
			LocalDate today,
			LocalDateTime now) throws java.sql.SQLException {
		long[] memberIds = batchInsert(connection,
				"INSERT INTO members(email,password_hash,role) VALUES (?,?,'USER')",
				cohortSize,
				(statement, index) -> {
					statement.setString(1, fixtureEmail(index));
					statement.setString(2, "synthetic-no-login");
				});
		long[] addressIds = batchInsert(connection,
				"INSERT INTO member_addresses(member_id,name,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,updated_at) VALUES (?,'synthetic','synthetic recipient','000-0000-0000','00000','synthetic address',NULL,?,?)",
				cohortSize,
				(statement, index) -> {
					statement.setLong(1, memberIds[index]);
					statement.setObject(2, now);
					statement.setObject(3, now);
				});
		batch(connection, "UPDATE members SET default_address_id=? WHERE id=?", cohortSize, (statement, index) -> {
			statement.setLong(1, addressIds[index]);
			statement.setLong(2, memberIds[index]);
		});
		batch(connection,
				"INSERT INTO billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at) VALUES (?,'TOSS',?,?,'ACTIVE',?)",
				cohortSize,
				(statement, index) -> {
					statement.setLong(1, memberIds[index]);
					statement.setString(2, "perf-ph10-002-customer-" + index);
					statement.setString(3, "perf-ph10-002-billing-" + index);
					statement.setObject(4, now);
				});
		long[] petIds = batchInsert(connection,
				"INSERT INTO pets(member_id,name,pet_type) VALUES (?,'synthetic pet','DOG')",
				cohortSize,
				(statement, index) -> statement.setLong(1, memberIds[index]));
		long[] subscriptionIds = batchInsert(connection,
				"INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,pet_id,status,version,current_snapshot_id,legacy_api_visible,mvp2_managed) VALUES (?,?,1,2,?,?,?,'ACTIVE',0,NULL,false,true)",
				cohortSize,
				(statement, index) -> {
					statement.setLong(1, memberIds[index]);
					statement.setLong(2, catalog.skuId());
					statement.setObject(3, today.minusWeeks(2));
					statement.setObject(4, today);
					statement.setLong(5, petIds[index]);
				});
		long[] snapshotIds = batchInsert(connection,
				"INSERT INTO subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks) VALUES (?,?,1000,2)",
				cohortSize,
				(statement, index) -> {
					statement.setLong(1, subscriptionIds[index]);
					statement.setLong(2, catalog.planVersionId());
				});
		batch(connection, "UPDATE subscriptions SET current_snapshot_id=? WHERE id=?", cohortSize, (statement, index) -> {
			statement.setLong(1, snapshotIds[index]);
			statement.setLong(2, subscriptionIds[index]);
		});
		batch(connection,
				"INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) VALUES (?,?,1)",
				cohortSize,
				(statement, index) -> {
					statement.setLong(1, snapshotIds[index]);
					statement.setLong(2, catalog.skuId());
				});
		batch(connection,
				"INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id) VALUES (? ,?,'SCHEDULED',NULL)",
				cohortSize,
				(statement, index) -> {
					statement.setLong(1, subscriptionIds[index]);
					statement.setObject(2, today);
				});
		batch(connection,
				"INSERT INTO subscription_shipping_snapshots(subscription_id,recipient_name,recipient_phone,postal_code,address_line1,address_line2,updated_at) VALUES (?,'synthetic recipient','000-0000-0000','00000','synthetic address',NULL,?)",
				cohortSize,
				(statement, index) -> {
					statement.setLong(1, subscriptionIds[index]);
					statement.setObject(2, now);
				});
		return new FixtureRows(memberIds.length, subscriptionIds.length);
	}

	private long[] batchInsert(Connection connection, String sql, int count, BatchBinder binder)
			throws java.sql.SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			for (int index = 0; index < count; index++) {
				binder.bind(statement, index);
				statement.addBatch();
			}
			statement.executeBatch();
			long[] keys = new long[count];
			try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
				int index = 0;
				while (generatedKeys.next() && index < count) {
					keys[index++] = generatedKeys.getLong(1);
				}
				if (index != count) {
					throw new IllegalStateException("Synthetic fixture generated-key cardinality mismatch");
				}
			}
			return keys;
		}
	}

	private void batch(Connection connection, String sql, int count, BatchBinder binder)
			throws java.sql.SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < count; index++) {
				binder.bind(statement, index);
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private int dueBacklog() {
		return jdbc.queryForObject("""
				SELECT COUNT(*)
				FROM subscription_schedules schedule
				JOIN subscriptions subscription ON subscription.id=schedule.subscription_id
				JOIN members member ON member.id=subscription.member_id
				LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id=schedule.id
				WHERE member.email LIKE ?
				  AND subscription.mvp2_managed=true
				  AND subscription.status='ACTIVE'
				  AND schedule.status='SCHEDULED'
				  AND schedule.scheduled_date<=?
				  AND existing_order.id IS NULL
				""", Integer.class, fixtureEmailLike(), LocalDate.ofInstant(clock.instant(), SEOUL));
	}

	private int fixtureMemberCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE email LIKE ?", Integer.class, fixtureEmailLike());
	}

	private int fixtureOrderCount() {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM subscription_orders result
				JOIN subscriptions subscription ON subscription.id=result.subscription_id
				JOIN members member ON member.id=subscription.member_id
				WHERE member.email LIKE ?
				""", Integer.class, fixtureEmailLike());
	}

	private int fixtureDuplicateScheduleOrderCount() {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM (
				  SELECT result.schedule_id
				  FROM subscription_orders result
				  JOIN subscriptions subscription ON subscription.id=result.subscription_id
				  JOIN members member ON member.id=subscription.member_id
				  WHERE member.email LIKE ?
				  GROUP BY result.schedule_id HAVING COUNT(*)>1
				) duplicate_schedule
				""", Integer.class, fixtureEmailLike());
	}

	private int fixtureFutureScheduleCount() {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM subscription_schedules schedule
				JOIN subscriptions subscription ON subscription.id=schedule.subscription_id
				JOIN members member ON member.id=subscription.member_id
				WHERE member.email LIKE ? AND schedule.status='SCHEDULED' AND schedule.scheduled_date>?
				""", Integer.class, fixtureEmailLike(), LocalDate.ofInstant(clock.instant(), SEOUL));
	}

	private long lastInsertId() {
		return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private static String fixtureEmail(int index) {
		return FIXTURE_EMAIL_PREFIX + index + FIXTURE_EMAIL_SUFFIX;
	}

	private static String fixtureEmailLike() {
		return FIXTURE_EMAIL_PREFIX + "%" + FIXTURE_EMAIL_SUFFIX;
	}

	private static double elapsedMilliseconds(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000.0;
	}

	private static double percentile(List<Double> sortedValues, double quantile) {
		if (sortedValues.isEmpty()) return 0;
		int index = Math.max(0, (int) Math.ceil(sortedValues.size() * quantile) - 1);
		return sortedValues.get(index);
	}

	@FunctionalInterface
	private interface BatchBinder {
		void bind(PreparedStatement statement, int index) throws java.sql.SQLException;
	}

	private record FixtureRows(int members, int subscriptions) {}
	private record CatalogFixture(long skuId, long planVersionId) {}

	public record FixtureSummary(int cohortSize, int initialBacklog, int batchSize, long fixedDelayMs) {}

	public record BatchMeasurement(
			int sequence,
			int processed,
			int created,
			int failures,
			int duplicateOrNoOp,
			double durationMs) {}

	public record DrainSummary(
			int initialBacklog,
			int finalBacklog,
			int batchCount,
			int processed,
			int created,
			int failures,
			int duplicateOrNoOp,
			double rawDrainElapsedMs,
			double ordersPerSecond,
			double batchDurationP50Ms,
			double batchDurationP95Ms,
			double batchDurationMaxMs,
			int defaultSchedulerBatchSize,
			long defaultSchedulerFixedDelayMs,
			int defaultSchedulerProjectedTicks,
			long defaultSchedulerProjectedCompletionMs,
			String projectionBasis,
			int databaseOrderCount,
			int duplicateScheduleOrderCount,
			int futureScheduleCount,
			boolean harnessFailure,
			List<BatchMeasurement> batches) {}
}
