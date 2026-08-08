package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class V4IdempotencyRetentionMigrationIntegrationTests {

	@Autowired private DataSource dataSource;

	@Test
	void v4BackfillsOnlyCompletedSuccessRowsAndAddsCleanupIndexesOnMySql84() throws Throwable {
		Flyway latest = flyway();
		Throwable primaryFailure = null;
		try {
			latest.clean();
			Flyway.configure()
					.dataSource(dataSource)
					.locations("classpath:db/migration")
					.cleanDisabled(false)
					.target("3")
					.load()
					.migrate();

			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			seedV3Fixtures(jdbc);
			LocalDateTime beforeMigration = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);

			latest.migrate();

			LocalDateTime afterMigration = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
			LocalDateTime creationCompletedAt = completedAt(jdbc, "subscription_creation_idempotency_results", "creation-success");
			LocalDateTime commandCompletedAt = completedAt(jdbc, "subscription_command_idempotency_results", "command-success");
			assertThat(creationCompletedAt).isBetween(beforeMigration, afterMigration);
			assertThat(commandCompletedAt).isEqualTo(creationCompletedAt);
			assertThat(completedAt(jdbc, "subscription_creation_idempotency_results", "creation-incomplete")).isNull();
			assertThat(completedAt(jdbc, "subscription_command_idempotency_results", "command-incomplete")).isNull();
			assertCompletedAtColumn(jdbc, "subscription_creation_idempotency_results");
			assertCompletedAtColumn(jdbc, "subscription_command_idempotency_results");
			assertThat(indexColumns(jdbc, "subscription_creation_idempotency_results", "idx_creation_idempotency_completed_at"))
					.containsExactly("completed_at", "member_id", "idempotency_key");
			assertThat(indexColumns(jdbc, "subscription_command_idempotency_results", "idx_command_idempotency_completed_at"))
					.containsExactly("completed_at", "member_id", "subscription_id", "command_type", "idempotency_key");
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(4);
			try (Connection connection = dataSource.getConnection()) {
				assertThat(connection.getMetaData().getDatabaseProductVersion()).startsWith("8.4");
			}
		} catch (Throwable failure) {
			primaryFailure = failure;
			throw failure;
		} finally {
			try {
				latest.clean();
				latest.migrate();
			} catch (Throwable restoreFailure) {
				if (primaryFailure != null) {
					primaryFailure.addSuppressed(restoreFailure);
				} else {
					throw restoreFailure;
				}
			}
		}
	}

	private Flyway flyway() {
		return Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.cleanDisabled(false)
				.load();
	}

	private void seedV3Fixtures(JdbcTemplate jdbc) {
		jdbc.update("INSERT INTO members(email,password_hash) VALUES ('v4-migration@example.test',?)", "x".repeat(60));
		long memberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO products(name,short_description,pet_type,display_status) VALUES ('V4 fixture','V4 fixture','DOG','PUBLIC')");
		long productId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO skus(product_id,name,price,subscribable,display_order) VALUES (?,'V4 fixture',1000,true,1)", productId);
		long skuId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date) VALUES (?,?,1,4,'2026-08-01','2026-08-29')", memberId, skuId);
		long subscriptionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbc.update("INSERT INTO subscription_creation_idempotency_results(member_id,idempotency_key,payload_fingerprint,subscription_id,response_status,response_body,location_header,etag_header) VALUES (?,'creation-success',?,?,201,JSON_OBJECT(),'/api/v2/subscriptions/1','\"0\"')", memberId, "0".repeat(64), subscriptionId);
		jdbc.update("INSERT INTO subscription_creation_idempotency_results(member_id,idempotency_key,payload_fingerprint) VALUES (?,'creation-incomplete',?)", memberId, "1".repeat(64));
		jdbc.update("INSERT INTO subscription_command_idempotency_results(member_id,subscription_id,command_type,idempotency_key,payload_fingerprint,response_status,response_body,etag_header) VALUES (?,?,'PAUSE','command-success',?,200,JSON_OBJECT(),'\"1\"')", memberId, subscriptionId, "2".repeat(64));
		jdbc.update("INSERT INTO subscription_command_idempotency_results(member_id,subscription_id,command_type,idempotency_key,payload_fingerprint) VALUES (?,?,'RESUME','command-incomplete',?)", memberId, subscriptionId, "3".repeat(64));
	}

	private LocalDateTime completedAt(JdbcTemplate jdbc, String table, String key) {
		return jdbc.queryForObject(
				"SELECT completed_at FROM " + table + " WHERE idempotency_key=?",
				LocalDateTime.class,
				key);
	}

	private void assertCompletedAtColumn(JdbcTemplate jdbc, String table) {
		Map<String, Object> column = jdbc.queryForMap(
				"SELECT data_type,datetime_precision,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name='completed_at'",
				table);
		assertThat(column.get("DATA_TYPE")).isEqualTo("datetime");
		assertThat(column.get("DATETIME_PRECISION")).isEqualTo(6L);
		assertThat(column.get("IS_NULLABLE")).isEqualTo("YES");
	}

	private List<String> indexColumns(JdbcTemplate jdbc, String table, String index) {
		return jdbc.queryForList(
				"SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=? ORDER BY seq_in_index",
				String.class,
				table,
				index);
	}
}
