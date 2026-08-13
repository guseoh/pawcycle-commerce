package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class V9SubscriptionOrderMigrationIntegrationTests {

	@Autowired private DataSource dataSource;

	@Test
	void v8UpgradeAndFreshMigrationCreateOrderSchemaAndEnforceOneOrderPerSchedule() throws Throwable {
		Flyway latest = flyway();
		Throwable primaryFailure = null;
		try {
			latest.clean();
			migrateTo("8");
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			assertThat(tableExists(jdbc, "subscription_orders")).isFalse();
			assertThat(tableExists(jdbc, "subscription_order_items")).isFalse();

			Fixture fixture = seedV8Fixture(jdbc);
			latest.migrate();

			assertOrderSchema(jdbc);
			insertOrder(jdbc, fixture);
			assertThatThrownBy(() -> insertOrder(jdbc, fixture))
					.isInstanceOf(DataIntegrityViolationException.class);
			assertThat(jdbc.queryForObject(
					"SELECT COUNT(*) FROM subscription_orders WHERE schedule_id=?",
					Integer.class,
					fixture.scheduleId())).isEqualTo(1);

			latest.clean();
			latest.migrate();
			assertOrderSchema(jdbc);
			assertThat(jdbc.queryForObject(
					"SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",
					Integer.class)).isEqualTo(15);
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

	private Fixture seedV8Fixture(JdbcTemplate jdbc) {
		jdbc.update(
				"INSERT INTO members(email,password_hash) VALUES ('sub-auto-migration@example.test',?)",
				"x".repeat(60));
		long memberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update(
				"INSERT INTO products(name,short_description,pet_type,display_status) "
						+ "VALUES ('SUB-AUTO migration','fixture','DOG','PUBLIC')");
		long productId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update(
				"INSERT INTO skus(product_id,name,price,subscribable,display_order) "
						+ "VALUES (?,'SUB-AUTO migration SKU',12000,true,1)",
				productId);
		long skuId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update(
				"INSERT INTO subscriptions("
						+ "member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,"
						+ "status,version,legacy_api_visible,mvp2_managed"
						+ ") VALUES (?,?,2,4,'2026-07-04','2026-08-01','ACTIVE',0,false,true)",
				memberId,
				skuId);
		long subscriptionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update(
				"INSERT INTO subscription_plans(name,target_pet_type,on_sale) "
						+ "VALUES ('SUB-AUTO migration plan','DOG',true)");
		long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update(
				"INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) "
						+ "VALUES (?,24000,false)",
				planId);
		long planVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update(
				"INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,2)",
				planVersionId,
				skuId);
		jdbc.update(
				"INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) "
						+ "VALUES (?,4)",
				planVersionId);
		jdbc.update(
				"UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?",
				planVersionId,
				planId);
		jdbc.update(
				"INSERT INTO subscription_snapshots("
						+ "subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks"
						+ ") VALUES (?,?,24000,4)",
				subscriptionId,
				planVersionId);
		long snapshotId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update(
				"INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) VALUES (?,?,2)",
				snapshotId,
				skuId);
		jdbc.update(
				"UPDATE subscriptions SET current_snapshot_id=? WHERE id=?",
				snapshotId,
				subscriptionId);
		jdbc.update(
				"INSERT INTO subscription_schedules("
						+ "subscription_id,scheduled_date,status,effective_snapshot_id"
						+ ") VALUES (?,'2026-08-01','SCHEDULED',NULL)",
				subscriptionId);
		long scheduleId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		return new Fixture(memberId, subscriptionId, scheduleId, snapshotId, planVersionId, skuId);
	}

	private void insertOrder(JdbcTemplate jdbc, Fixture fixture) {
		jdbc.update(
				"INSERT INTO subscription_orders("
						+ "member_id,subscription_id,schedule_id,effective_snapshot_id,"
						+ "source_plan_version_id,scheduled_date,processed_at,package_total_krw,status"
						+ ") VALUES (?,?,?,?,?,?,?,?, 'CREATED')",
				fixture.memberId(),
				fixture.subscriptionId(),
				fixture.scheduleId(),
				fixture.snapshotId(),
				fixture.planVersionId(),
				LocalDate.of(2026, 8, 1),
				LocalDateTime.of(2026, 8, 1, 0, 0),
				24000);
		long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update(
				"INSERT INTO subscription_order_items(order_id,sku_id,quantity) VALUES (?,?,2)",
				orderId,
				fixture.skuId());
	}

	private void assertOrderSchema(JdbcTemplate jdbc) {
		assertThat(tableExists(jdbc, "subscription_orders")).isTrue();
		assertThat(tableExists(jdbc, "subscription_order_items")).isTrue();
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM information_schema.table_constraints "
						+ "WHERE constraint_schema=DATABASE() AND table_name='subscription_orders' "
						+ "AND constraint_name='uk_subscription_orders_schedule' "
						+ "AND constraint_type='UNIQUE'",
				Integer.class)).isEqualTo(1);
		assertThat(indexColumns(jdbc, "subscription_schedules", "idx_schedules_due_automation"))
				.containsExactly("status", "scheduled_date", "id", "subscription_id");
	}

	private boolean tableExists(JdbcTemplate jdbc, String table) {
		return jdbc.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables "
						+ "WHERE table_schema=DATABASE() AND table_name=?",
				Integer.class,
				table) == 1;
	}

	private List<String> indexColumns(JdbcTemplate jdbc, String table, String index) {
		return jdbc.queryForList(
				"SELECT column_name FROM information_schema.statistics "
						+ "WHERE table_schema=DATABASE() AND table_name=? AND index_name=? "
						+ "ORDER BY seq_in_index",
				String.class,
				table,
				index);
	}

	private void migrateTo(String version) {
		Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.cleanDisabled(false)
				.target(version)
				.load()
				.migrate();
	}

	private Flyway flyway() {
		return Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.cleanDisabled(false)
				.load();
	}

	private record Fixture(
			long memberId,
			long subscriptionId,
			long scheduleId,
			long snapshotId,
			long planVersionId,
			long skuId) {}
}
