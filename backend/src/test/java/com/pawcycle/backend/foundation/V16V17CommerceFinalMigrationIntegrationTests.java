package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
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
	void finalCommerceTablesAndCriticalConstraintsAreAppliedWithoutPaidDeliveryBackfill() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()", String.class);
		assertThat(tables).contains("deliveries", "order_cancellations", "order_returns", "refunds", "notifications", "admin_audit_logs");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deliveries", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='deliveries' AND constraint_name='uk_deliveries_order'", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='refunds' AND constraint_name='uk_refunds_succeeded_order'", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(17);
	}
}
