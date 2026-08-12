package com.pawcycle.backend.subscription.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OpsPerf002MigrationMeasurementTests {

	private static final int ROWS = envInt("OPS_PERF_002_ROWS");
	private static final int WARMUP = envInt("OPS_PERF_002_WARMUP");
	private static final int ITERATIONS = envInt("OPS_PERF_002_ITERATIONS");
	private static final int LOCK_ITERATIONS = envInt("OPS_PERF_002_LOCK_ITERATIONS");
	private static final int LOCK_ROWS = envInt("OPS_PERF_002_LOCK_ROWS");
	private static final String RUN_ID = env("OPS_PERF_002_RUN_ID");

	@Autowired private LegacyMvp2MigrationService migration;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private DataSource dataSource;

	@Test
	void measureIsolatedLocalMigrationAndActualQueryLockFootprint() throws Exception {
		assertThat(legacyRows()).as("pre-existing legacy rows in isolated database").isZero();
		Map<String, Object> evidence = metadata();
		evidence.put("migration_execution", measureMigrationRuns());
		evidence.put("rollback_probe", measureRollback());
		evidence.put("lock_footprint", measureLockFootprint());
		evidence.put("final_legacy_rows", legacyRows());
		evidence.put("production_execution", "none");

		Path output = Path.of(env("OPS_PERF_002_OUTPUT"));
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, toJson(evidence) + System.lineSeparator());
	}

	private Map<String, Object> metadata() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("schema_version", "1.0");
		result.put("task_id", "OPS-PERF-002");
		result.put("run_id", RUN_ID);
		result.put("source_commit", env("OPS_PERF_002_SOURCE_COMMIT"));
		result.put("measurement_script", "docs/performance/OPS-PERF-002-local-migration-measurement.ps1");
		result.put("measurement_script_sha256", env("OPS_PERF_002_SCRIPT_SHA256"));
		result.put("test_harness", "docs/performance/fixtures/OpsPerf002MigrationMeasurementTests.java");
		result.put("test_harness_sha256", env("OPS_PERF_002_HARNESS_SHA256"));
		result.put("mysql_image", env("OPS_PERF_002_MYSQL_IMAGE"));
		result.put("mysql_image_digest", env("OPS_PERF_002_MYSQL_DIGEST"));
		result.put("mysql_version", env("OPS_PERF_002_MYSQL_VERSION"));
		result.put("java_image", env("OPS_PERF_002_JAVA_IMAGE"));
		result.put("execution", "isolated_local_migration_execution");
		result.put("native_command_nonzero_check", env("OPS_PERF_002_NATIVE_EXIT_CHECK"));
		result.put("fixture", Map.of(
			"legacy_rows", ROWS,
			"warmup_runs", WARMUP,
			"measured_runs", ITERATIONS,
			"lock_runs", LOCK_ITERATIONS,
			"lock_legacy_rows", LOCK_ROWS
		));
		return result;
	}

	private Map<String, Object> measureMigrationRuns() throws Exception {
		List<Map<String, Object>> runs = new ArrayList<>();
		List<Double> samples = new ArrayList<>();
		for (int run = 1; run <= WARMUP + ITERATIONS; run++) {
			Fixture fixture = createFixture("migration-" + run, ROWS, false);
			Throwable primaryFailure = null;
			try {
				assertThat(migration.preflight().valid()).isTrue();
				double duration = timeMillis(() -> migration.migrateAfterSourceWriteFreeze(true));
				assertMigrated(fixture, ROWS);
				boolean warmup = run <= WARMUP;
				if (!warmup) samples.add(duration);
				runs.add(Map.of(
					"run_id", RUN_ID + "-migration-" + run,
					"fixture_id", fixture.token(),
					"kind", warmup ? "warmup" : "sample",
					"rows", ROWS,
					"duration_ms", duration,
					"post_validation", "pass"
				));
			} catch (Exception | Error failure) {
				primaryFailure = failure;
				throw failure;
			} finally {
				cleanupPreserving(fixture, primaryFailure);
			}
			assertFixtureRemoved(fixture);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("runs", runs);
		result.put("sample_median_ms", median(samples));
		result.put("sample_min_ms", Collections.min(samples));
		result.put("sample_max_ms", Collections.max(samples));
		result.put("post_validation", "pass");
		result.put("cleanup_validation", "pass");
		return result;
	}

	private Map<String, Object> measureRollback() {
		Fixture fixture = createFixture("rollback", 1, true);
		Throwable primaryFailure = null;
		try {
			assertThatThrownBy(() -> migration.migrateAfterSourceWriteFreeze(true))
				.isInstanceOf(RuntimeException.class);
			assertThat(jdbc.queryForObject(
				"SELECT mvp2_managed FROM subscriptions WHERE member_id=?", Boolean.class, fixture.memberId()
			)).isFalse();
			assertThat(jdbc.queryForObject(
				"SELECT current_snapshot_id FROM subscriptions WHERE member_id=?", Long.class, fixture.memberId()
			)).isNull();
			assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM subscription_snapshots ss
				JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id=?
				""", Integer.class, fixture.memberId())).isZero();
			return Map.of(
				"run_id", RUN_ID + "-rollback-1",
				"fixture_id", fixture.token(),
				"failure_injection", "duplicate subscription_schedules(subscription_id, scheduled_date)",
				"transaction_rollback", "pass",
				"assertions", List.of("mvp2_managed=false", "current_snapshot_id=null", "migration_snapshot_count=0"),
				"cleanup_validation", "pass"
			);
		} catch (RuntimeException | Error failure) {
			primaryFailure = failure;
			throw failure;
		} finally {
			cleanupPreserving(fixture, primaryFailure);
		}
	}

	private Map<String, Object> measureLockFootprint() throws Exception {
		List<Map<String, Object>> runs = new ArrayList<>();
		for (int run = 1; run <= LOCK_ITERATIONS; run++) {
			LockFixture fixture = createLockFixture("lock-" + run, LOCK_ROWS);
			Throwable primaryFailure = null;
			ExecutorService executor = Executors.newFixedThreadPool(4);
			try {
				WriterResult targetBaseline = updateBaseline("legacy_target_update", fixture.targetSubscriptionId());
				WriterResult managedBaseline = updateBaseline("managed_row_update", fixture.managedSubscriptionId());
				WriterResult insertBaseline = insertBaseline(fixture);

				Future<Double> migrationFuture = executor.submit(
					() -> timeMillis(() -> migration.migrateAfterSourceWriteFreeze(true))
				);
				int grantedRecordLocks = awaitMigrationRecordLocks(migrationFuture);
				CountDownLatch ready = new CountDownLatch(3);
				CountDownLatch start = new CountDownLatch(1);
				Map<String, Long> connectionIds = new ConcurrentHashMap<>();
				Future<WriterResult> targetWriter = executor.submit(() -> updateWriter(
					"legacy_target_update", fixture.targetSubscriptionId(), ready, start, connectionIds
				));
				Future<WriterResult> managedWriter = executor.submit(() -> updateWriter(
					"managed_row_update", fixture.managedSubscriptionId(), ready, start, connectionIds
				));
				Future<WriterResult> adjacentInsert = executor.submit(() -> insertWriter(
					"adjacent_managed_insert", fixture, ready, start, connectionIds
				));
				assertThat(ready.await(10, TimeUnit.SECONDS)).as("writer connections ready").isTrue();
				start.countDown();
				Set<String> observedWaitOperations = observeWaitingOperations(connectionIds, migrationFuture);

				WriterResult target = targetWriter.get(30, TimeUnit.SECONDS);
				WriterResult managed = managedWriter.get(30, TimeUnit.SECONDS);
				WriterResult insert = adjacentInsert.get(30, TimeUnit.SECONDS);
				double migrationDuration = migrationFuture.get(30, TimeUnit.SECONDS);
				assertMigrated(fixture.base(), LOCK_ROWS);
				assertThat(jdbc.queryForObject(
					"SELECT current_snapshot_id FROM subscriptions WHERE id=?",
					Long.class, fixture.managedSubscriptionId()
				)).isNull();

				Map<String, Object> baselines = new LinkedHashMap<>();
				baselines.put(targetBaseline.operation(), targetBaseline.durationMs());
				baselines.put(managedBaseline.operation(), managedBaseline.durationMs());
				baselines.put(insertBaseline.operation(), insertBaseline.durationMs());
				Map<String, Object> contention = new LinkedHashMap<>();
				contention.put(target.operation(), target.durationMs());
				contention.put(managed.operation(), managed.durationMs());
				contention.put(insert.operation(), insert.durationMs());
				runs.add(Map.of(
					"run_id", RUN_ID + "-lock-" + run,
					"fixture_id", fixture.base().token(),
					"legacy_target_rows", LOCK_ROWS,
					"managed_rows_before_migration", 1,
					"migration_duration_ms", migrationDuration,
					"granted_subscription_record_locks_observed", grantedRecordLocks,
					"baseline_ms", baselines,
					"contention_ms", contention,
					"data_lock_wait_operations", observedWaitOperations,
					"post_validation", "pass"
				));
			} catch (Exception | Error failure) {
				primaryFailure = failure;
				throw failure;
			} finally {
				executor.shutdownNow();
				cleanupPreserving(fixture.base(), primaryFailure);
			}
			assertFixtureRemoved(fixture.base());
		}
		return Map.of(
			"query_path", "LegacyMvp2MigrationService.migrateAfterSourceWriteFreeze(true)",
			"lock_signal", "performance_schema.data_locks granted RECORD lock on subscriptions",
			"wait_attribution", "performance_schema.data_lock_waits requesting connection id",
			"runs", runs,
			"cleanup_validation", "pass"
		);
	}

	private int awaitMigrationRecordLocks(Future<Double> migrationFuture) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			Integer locks = jdbc.queryForObject("""
				SELECT COUNT(*) FROM performance_schema.data_locks
				WHERE OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='subscriptions'
				  AND LOCK_TYPE='RECORD' AND LOCK_STATUS='GRANTED'
				""", Integer.class);
			if (locks != null && locks > 0) return locks;
			if (migrationFuture.isDone()) {
				migrationFuture.get();
				throw new AssertionError("migration completed before subscriptions record locks were observed");
			}
			Thread.sleep(2);
		}
		throw new AssertionError("subscriptions record lock observation timed out");
	}

	private Set<String> observeWaitingOperations(
		Map<String, Long> connectionIds,
		Future<Double> migrationFuture
	) throws InterruptedException {
		Set<String> observed = new LinkedHashSet<>();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline && !migrationFuture.isDone()) {
			List<Long> waitingConnections = jdbc.queryForList("""
				SELECT DISTINCT t.PROCESSLIST_ID
				FROM performance_schema.data_lock_waits w
				JOIN performance_schema.data_locks l
				  ON l.ENGINE=w.ENGINE AND l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
				JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID
				WHERE t.PROCESSLIST_ID IS NOT NULL
				""", Long.class);
			connectionIds.forEach((operation, connectionId) -> {
				if (waitingConnections.contains(connectionId)) observed.add(operation);
			});
			Thread.sleep(2);
		}
		return observed;
	}

	private WriterResult updateBaseline(String operation, long subscriptionId) throws Exception {
		try (Connection connection = dataSource.getConnection();
			 PreparedStatement statement = connection.prepareStatement(
				 "UPDATE subscriptions SET quantity=quantity WHERE id=?")) {
			statement.setLong(1, subscriptionId);
			double duration = timeMillis(() -> assertThat(statement.executeUpdate()).isEqualTo(1));
			return new WriterResult(operation, duration);
		}
	}

	private WriterResult insertBaseline(LockFixture fixture) throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			TimedInsert inserted = timedManagedInsert(connection, fixture);
			try (PreparedStatement cleanup = connection.prepareStatement("DELETE FROM subscriptions WHERE id=?")) {
				cleanup.setLong(1, inserted.subscriptionId());
				assertThat(cleanup.executeUpdate()).isEqualTo(1);
			}
			return new WriterResult("adjacent_managed_insert", inserted.durationMs());
		}
	}

	private WriterResult updateWriter(
		String operation,
		long subscriptionId,
		CountDownLatch ready,
		CountDownLatch start,
		Map<String, Long> connectionIds
	) throws Exception {
		try (Connection connection = dataSource.getConnection();
			 PreparedStatement statement = connection.prepareStatement(
				 "UPDATE subscriptions SET quantity=quantity WHERE id=?")) {
			connectionIds.put(operation, connectionId(connection));
			statement.setLong(1, subscriptionId);
			ready.countDown();
			start.await();
			double duration = timeMillis(() -> assertThat(statement.executeUpdate()).isEqualTo(1));
			return new WriterResult(operation, duration);
		}
	}

	private WriterResult insertWriter(
		String operation,
		LockFixture fixture,
		CountDownLatch ready,
		CountDownLatch start,
		Map<String, Long> connectionIds
	) throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			connectionIds.put(operation, connectionId(connection));
			ready.countDown();
			start.await();
			TimedInsert inserted = timedManagedInsert(connection, fixture);
			assertThat(inserted.subscriptionId()).isPositive();
			return new WriterResult(operation, inserted.durationMs());
		}
	}

	private TimedInsert timedManagedInsert(Connection connection, LockFixture fixture) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement("""
			INSERT INTO subscriptions(
			  member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,
			  status,version,legacy_api_visible,mvp2_managed
			) VALUES (?,?,1,4,CURDATE(),DATE_ADD(CURDATE(),INTERVAL 4 WEEK),'ACTIVE',0,false,true)
			""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, fixture.base().memberId());
			statement.setLong(2, fixture.base().skuId());
			double duration = timeMillis(() -> assertThat(statement.executeUpdate()).isEqualTo(1));
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertThat(keys.next()).isTrue();
				return new TimedInsert(keys.getLong(1), duration);
			}
		}
	}

	private long connectionId(Connection connection) throws Exception {
		try (Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery("SELECT CONNECTION_ID()")) {
			assertThat(result.next()).isTrue();
			return result.getLong(1);
		}
	}

	private Fixture createFixture(String suffix, int rows, boolean duplicateSchedule) {
		Fixture fixture = createParents(suffix, rows);
		insertLegacyRows(fixture.memberId(), fixture.skuId(), rows);
		if (duplicateSchedule) {
			long subscriptionId = jdbc.queryForObject(
				"SELECT id FROM subscriptions WHERE member_id=?", Long.class, fixture.memberId()
			);
			jdbc.update(
				"INSERT INTO subscription_schedules(subscription_id,scheduled_date,status) VALUES (?,?,'SCHEDULED')",
				subscriptionId, LocalDate.now(ZoneId.of("Asia/Seoul")).plusWeeks(4)
			);
		}
		return fixture;
	}

	private LockFixture createLockFixture(String suffix, int rows) {
		Fixture fixture = createParents(suffix, rows);
		int firstHalf = rows / 2;
		insertLegacyRows(fixture.memberId(), fixture.skuId(), firstHalf);
		long managedId = insertManagedSubscription(fixture.memberId(), fixture.skuId());
		insertLegacyRows(fixture.memberId(), fixture.skuId(), rows - firstHalf);
		long targetId = jdbc.queryForObject(
			"SELECT MIN(id) FROM subscriptions WHERE member_id=? AND mvp2_managed=false",
			Long.class, fixture.memberId()
		);
		return new LockFixture(fixture, targetId, managedId);
	}

	private Fixture createParents(String suffix, int rows) {
		String token = RUN_ID + "-" + suffix + "-" + UUID.randomUUID();
		String email = token.toLowerCase() + "@example.test";
		jdbc.update("INSERT INTO members(email,password_hash) VALUES (?,?)", email, "local-measurement-only");
		long memberId = jdbc.queryForObject("SELECT id FROM members WHERE email=?", Long.class, email);
		jdbc.update(
			"INSERT INTO products(name,short_description,pet_type,display_status) VALUES (?,?,'DOG','PUBLIC')",
			token, "local measurement"
		);
		long productId = jdbc.queryForObject("SELECT id FROM products WHERE name=?", Long.class, token);
		jdbc.update(
			"""
			INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status)
			VALUES (?,?,?,12000,true,1,'ACTIVE')
			""",
			productId, "OPS-PERF-002-" + UUID.randomUUID(), token
		);
		long skuId = jdbc.queryForObject(
			"SELECT id FROM skus WHERE product_id=? AND name=?", Long.class, productId, token
		);
		return new Fixture(token, email, memberId, productId, skuId, rows);
	}

	private void insertLegacyRows(long memberId, long skuId, int rows) {
		if (rows == 0) return;
		jdbc.update("""
			INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date)
			SELECT ?,?,1,4,CURDATE(),DATE_ADD(CURDATE(),INTERVAL 4 WEEK)
			FROM (WITH RECURSIVE seq AS (
			  SELECT 1 n UNION ALL SELECT n+1 FROM seq WHERE n<?
			) SELECT n FROM seq) q
			""", memberId, skuId, rows);
	}

	private long insertManagedSubscription(long memberId, long skuId) {
		jdbc.update("""
			INSERT INTO subscriptions(
			  member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,
			  status,version,legacy_api_visible,mvp2_managed
			) VALUES (?,?,1,4,CURDATE(),DATE_ADD(CURDATE(),INTERVAL 4 WEEK),'ACTIVE',0,false,true)
			""", memberId, skuId);
		return jdbc.queryForObject(
			"SELECT MAX(id) FROM subscriptions WHERE member_id=? AND mvp2_managed=true",
			Long.class, memberId
		);
	}

	private void assertMigrated(Fixture fixture, int expectedRows) {
		assertThat(jdbc.queryForObject("""
			SELECT COUNT(*) FROM subscriptions
			WHERE member_id=? AND mvp2_managed=true AND status='ACTIVE'
			  AND current_snapshot_id IS NOT NULL AND legacy_api_visible=false
			""", Integer.class, fixture.memberId())).isEqualTo(expectedRows);
		assertThat(jdbc.queryForObject("""
			SELECT COUNT(*) FROM subscription_snapshots ss
			JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id=?
			""", Integer.class, fixture.memberId())).isEqualTo(expectedRows);
		assertThat(jdbc.queryForObject("""
			SELECT COUNT(*) FROM subscription_snapshot_items si
			JOIN subscription_snapshots ss ON ss.id=si.snapshot_id
			JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id=?
			""", Integer.class, fixture.memberId())).isEqualTo(expectedRows);
		assertThat(jdbc.queryForObject("""
			SELECT COUNT(*) FROM subscription_schedules sc
			JOIN subscriptions s ON s.id=sc.subscription_id
			WHERE s.member_id=? AND sc.status='SCHEDULED'
			""", Integer.class, fixture.memberId())).isEqualTo(expectedRows);
	}

	private void cleanup(Fixture fixture) {
		List<Long> versionIds = jdbc.queryForList("""
			SELECT DISTINCT ss.source_plan_version_id FROM subscription_snapshots ss
			JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id=?
			""", Long.class, fixture.memberId());
		List<Long> planIds = versionIds.isEmpty() ? List.of() : jdbc.queryForList(
			"SELECT DISTINCT plan_id FROM plan_versions WHERE id IN " + placeholders(versionIds.size()),
			Long.class, versionIds.toArray()
		);
		jdbc.update("DELETE sc FROM subscription_schedules sc JOIN subscriptions s ON s.id=sc.subscription_id WHERE s.member_id=?", fixture.memberId());
		jdbc.update("DELETE si FROM subscription_snapshot_items si JOIN subscription_snapshots ss ON ss.id=si.snapshot_id JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id=?", fixture.memberId());
		jdbc.update("UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id=?", fixture.memberId());
		jdbc.update("DELETE ss FROM subscription_snapshots ss JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id=?", fixture.memberId());
		jdbc.update("DELETE FROM subscriptions WHERE member_id=?", fixture.memberId());
		if (!planIds.isEmpty()) {
			jdbc.update("UPDATE subscription_plans SET current_plan_version_id=NULL WHERE id IN " + placeholders(planIds.size()), planIds.toArray());
			jdbc.update("DELETE FROM plan_version_delivery_cycles WHERE plan_version_id IN " + placeholders(versionIds.size()), versionIds.toArray());
			jdbc.update("DELETE FROM plan_items WHERE plan_version_id IN " + placeholders(versionIds.size()), versionIds.toArray());
			jdbc.update("DELETE FROM plan_versions WHERE id IN " + placeholders(versionIds.size()), versionIds.toArray());
			jdbc.update("DELETE FROM subscription_plans WHERE id IN " + placeholders(planIds.size()), planIds.toArray());
		}
		jdbc.update("DELETE FROM skus WHERE id=?", fixture.skuId());
		jdbc.update("DELETE FROM products WHERE id=?", fixture.productId());
		jdbc.update("DELETE FROM members WHERE id=?", fixture.memberId());
	}

	private void cleanupPreserving(Fixture fixture, Throwable primaryFailure) {
		try {
			cleanup(fixture);
		} catch (RuntimeException cleanupFailure) {
			if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
			else throw cleanupFailure;
		}
	}

	private void assertFixtureRemoved(Fixture fixture) {
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE email=?", Integer.class, fixture.email())).isZero();
		assertThat(legacyRows()).isZero();
	}

	private int legacyRows() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions WHERE mvp2_managed=false", Integer.class);
	}

	private static double timeMillis(ThrowingRunnable runnable) throws Exception {
		long started = System.nanoTime();
		runnable.run();
		return Math.round((System.nanoTime() - started) / 100_000.0) / 10.0;
	}

	private static double median(List<Double> values) {
		List<Double> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		int middle = sorted.size() / 2;
		return sorted.size() % 2 == 1
			? sorted.get(middle)
			: Math.round((sorted.get(middle - 1) + sorted.get(middle)) * 5.0) / 10.0;
	}

	private static String placeholders(int count) {
		return "(" + String.join(",", Collections.nCopies(count, "?")) + ")";
	}

	private static String toJson(Object value) {
		if (value == null) return "null";
		if (value instanceof String text) return '"' + escapeJson(text) + '"';
		if (value instanceof Number || value instanceof Boolean) return value.toString();
		if (value instanceof Map<?, ?> map) {
			List<String> entries = new ArrayList<>();
			map.forEach((key, item) -> entries.add(toJson(key.toString()) + ":" + toJson(item)));
			return "{" + String.join(",", entries) + "}";
		}
		if (value instanceof Iterable<?> iterable) {
			List<String> items = new ArrayList<>();
			iterable.forEach(item -> items.add(toJson(item)));
			return "[" + String.join(",", items) + "]";
		}
		throw new IllegalArgumentException("unsupported JSON evidence type: " + value.getClass().getName());
	}

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\r", "\\r")
			.replace("\n", "\\n")
			.replace("\t", "\\t");
	}

	private static String env(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
		return value;
	}

	private static int envInt(String name) { return Integer.parseInt(env(name)); }

	@FunctionalInterface
	private interface ThrowingRunnable { void run() throws Exception; }

	private record Fixture(String token, String email, long memberId, long productId, long skuId, int rows) {}
	private record LockFixture(Fixture base, long targetSubscriptionId, long managedSubscriptionId) {}
	private record WriterResult(String operation, double durationMs) {}
	private record TimedInsert(long subscriptionId, double durationMs) {}
}
