package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class V13CommerceSupportingDomainsMigrationIntegrationTests {
	@Autowired private DataSource dataSource;

	@Test
	void v13BackfillsUncategorizedProductsAndCreatesInventoryForLegacySkus() throws Throwable {
		Flyway latest = flyway();
		Throwable failure = null;
		try {
			latest.clean();
			migrateTo("12");
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			jdbc.update("INSERT INTO products(name,short_description,pet_type,display_status) VALUES ('legacy','legacy','DOG','PUBLIC')");
			long productId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
			jdbc.update("INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES (?,'LEGACY-1','legacy',1000,true,1,'ACTIVE')", productId);
			long skuId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

			migrateTo("13");

			assertThat(jdbc.queryForObject("SELECT slug FROM categories WHERE id=(SELECT category_id FROM products WHERE id=?)", String.class, productId)).isEqualTo("__pawcycle_uncategorized__");
			assertThat(jdbc.queryForObject("SELECT active FROM categories WHERE slug='__pawcycle_uncategorized__'", Boolean.class)).isFalse();
			assertThat(jdbc.queryForObject("SELECT available_quantity FROM inventories WHERE sku_id=?", Integer.class, skuId)).isZero();
			assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='products' AND column_name='category_id'", String.class)).isEqualTo("NO");
		} catch (Throwable current) {
			failure = current;
			throw current;
		} finally {
			try { latest.clean(); latest.migrate(); }
			catch (Throwable restore) { if (failure != null) failure.addSuppressed(restore); else throw restore; }
		}
	}

	private void migrateTo(String version) { Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).target(version).load().migrate(); }
	private Flyway flyway() { return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).load(); }
}
