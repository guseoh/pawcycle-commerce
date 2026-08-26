package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class V16V17CommerceFinalMigrationIntegrationTests {
	@Autowired private DataSource dataSource;

	@Test
	void finalCommerceTablesAndCriticalConstraintsAreAppliedWithoutPaidDeliveryBackfill() throws Throwable {
		Flyway latest = flyway();
		Throwable primaryFailure = null;
		try {
			latest.clean();
			migrateTo("15");
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			long paidOrderId = seedPaidV15Order(jdbc);

			latest.migrate();

			List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()", String.class);
			assertThat(tables).contains("deliveries", "order_cancellations", "order_returns", "refunds", "notifications", "admin_audit_logs",
					"product_detail_sections", "reviews", "product_questions");
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deliveries WHERE order_id=?", Integer.class, paidOrderId)).isZero();
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='deliveries' AND constraint_name='uk_deliveries_order'", Integer.class)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='refunds' AND constraint_name='uk_refunds_succeeded_order'", Integer.class)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='refunds' AND constraint_name='uk_refunds_source_attempt'", Integer.class)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='billing_payment_method_preparations' AND column_name='status'", Integer.class)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='billing_payment_method_preparations' AND column_name='claimed_at'", Integer.class)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT check_clause FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_notifications_type'", String.class)).contains("PRODUCT_QUESTION_ANSWERED");
			assertThat(jdbc.queryForObject("SELECT check_clause FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='chk_refunds_amount'", String.class)).contains(">= 0");
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(21);
		} catch (Throwable failure) {
			primaryFailure = failure;
			throw failure;
		} finally {
			try {
				latest.clean();
				latest.migrate();
			} catch (Throwable restoreFailure) {
				if (primaryFailure != null) primaryFailure.addSuppressed(restoreFailure);
				else throw restoreFailure;
			}
		}
	}

	private long seedPaidV15Order(JdbcTemplate jdbc) {
		jdbc.update("INSERT INTO members(email,password_hash,role) VALUES ('mvp3-final-migration@example.test',?,'USER')", "x".repeat(60));
		long memberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		Timestamp now = Timestamp.from(Instant.parse("2026-08-13T00:00:00Z"));
		jdbc.update("""
			INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,created_at,paid_at)
			VALUES (?,?, 'ONE_TIME','PAID',?,?,?,?,?,?)
			""", "MVP3-FINAL-MIGRATION-PAID", memberId, BigDecimal.valueOf(10000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(10000), now, now);
		return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
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
}
