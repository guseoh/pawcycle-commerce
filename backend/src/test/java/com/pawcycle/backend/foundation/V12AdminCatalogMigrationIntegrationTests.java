package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class V12AdminCatalogMigrationIntegrationTests {

  @Autowired private DataSource dataSource;

  @Test
  void v12BackfillsLegacyMemberAndSkuAndCreatesCatalogConstraints() throws Throwable {
    Flyway latest = flyway();
    Throwable primaryFailure = null;
    try {
      latest.clean();
      migrateTo("11");
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      jdbc.update(
          "INSERT INTO members(email,password_hash) VALUES ('v12@example.test',?)", "x".repeat(60));
      long memberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      jdbc.update(
          "INSERT INTO products(name,short_description,pet_type,display_status) VALUES"
              + " ('V12','V12','DOG','PUBLIC')");
      long productId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      jdbc.update(
          "INSERT INTO skus(product_id,name,price,subscribable,display_order) VALUES"
              + " (?,'legacy',1000,true,1)",
          productId);
      long skuId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

      migrateTo("12");

      assertThat(jdbc.queryForObject("SELECT role FROM members WHERE id=?", String.class, memberId))
          .isEqualTo("USER");
      assertThat(jdbc.queryForObject("SELECT sku_code FROM skus WHERE id=?", String.class, skuId))
          .isEqualTo("SKU-" + skuId);
      assertThat(jdbc.queryForObject("SELECT status FROM skus WHERE id=?", String.class, skuId))
          .isEqualTo("ACTIVE");
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND"
                      + " table_name='categories'",
                  Integer.class))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT display_status FROM products WHERE id=?", String.class, productId))
          .isEqualTo("PUBLIC");
      jdbc.update(
          "INSERT INTO members(email,password_hash) VALUES ('v12-default@example.test',?)",
          "y".repeat(60));
      assertThat(
              jdbc.queryForObject(
                  "SELECT role FROM members WHERE email='v12-default@example.test'", String.class))
          .isEqualTo("USER");

      assertConstraint(jdbc, "members", "chk_members_role", "CHECK");
      assertConstraint(jdbc, "categories", "uk_categories_slug", "UNIQUE");
      assertConstraint(jdbc, "categories", "chk_categories_display_order_nonnegative", "CHECK");
      assertConstraint(jdbc, "products", "fk_products_category", "FOREIGN KEY");
      assertConstraint(jdbc, "skus", "uk_skus_sku_code", "UNIQUE");
      assertConstraint(jdbc, "skus", "chk_skus_status", "CHECK");
      assertNotNullable(jdbc, "members", "role");
      assertNotNullable(jdbc, "categories", "name");
      assertNotNullable(jdbc, "categories", "slug");
      assertNotNullable(jdbc, "categories", "display_order");
      assertNotNullable(jdbc, "categories", "active");
      assertNotNullable(jdbc, "skus", "sku_code");
      assertNotNullable(jdbc, "skus", "status");
    } catch (Throwable failure) {
      primaryFailure = failure;
      throw failure;
    } finally {
      restoreLatest(latest, primaryFailure);
    }
  }

  @Test
  void v12StopsBeforeBackfillWhenLegacyProductStatusIsUnknown() throws Throwable {
    Flyway latest = flyway();
    Throwable primaryFailure = null;
    try {
      latest.clean();
      migrateTo("11");
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      jdbc.update(
          "INSERT INTO products(name,short_description,pet_type,display_status) VALUES"
              + " ('legacy','legacy','DOG','HIDDEN')");

      assertThatThrownBy(() -> migrateTo("12"))
          .hasMessageContaining("V12__add_admin_catalog_and_rbac.sql");
      assertThat(columnExists(jdbc, "members", "role")).isFalse();
      assertThat(tableExists(jdbc, "categories")).isFalse();
      assertThat(columnExists(jdbc, "skus", "sku_code")).isFalse();
    } catch (Throwable failure) {
      primaryFailure = failure;
      throw failure;
    } finally {
      restoreLatest(latest, primaryFailure);
    }
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

  private void restoreLatest(Flyway latest, Throwable primaryFailure) throws Throwable {
    try {
      latest.clean();
      latest.migrate();
    } catch (Throwable restoreFailure) {
      if (primaryFailure != null) primaryFailure.addSuppressed(restoreFailure);
      else throw restoreFailure;
    }
  }

  private boolean columnExists(JdbcTemplate jdbc, String table, String column) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND"
                + " table_name=? AND column_name=?",
            Integer.class,
            table,
            column)
        == 1;
  }

  private boolean tableExists(JdbcTemplate jdbc, String table) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND"
                + " table_name=?",
            Integer.class,
            table)
        == 1;
  }

  private void assertConstraint(JdbcTemplate jdbc, String table, String constraint, String type) {
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema=DATABASE()
                  AND table_name=?
                  AND constraint_name=?
                  AND constraint_type=?
                """,
                Integer.class,
                table,
                constraint,
                type))
        .isEqualTo(1);
  }

  private void assertNotNullable(JdbcTemplate jdbc, String table, String column) {
    assertThat(
            jdbc.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name=? AND column_name=?
                """,
                String.class,
                table,
                column))
        .isEqualTo("NO");
  }
}
