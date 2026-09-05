package com.pawcycle.backend.foundation.bootstrap;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalQaSubscriptionFixtureService {

  static final String PLAN_NAME = "[QA OPS-031] 기본 패키지";
  static final long PACKAGE_PRICE_KRW = 19_900L;
  static final List<Integer> DELIVERY_CYCLES = List.of(2, 4, 8);

  private final JdbcTemplate jdbcTemplate;

  public LocalQaSubscriptionFixtureService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public void bootstrap() {
    long skuId = fixtureSkuId();
    List<Map<String, Object>> plans =
        jdbcTemplate.queryForList(
            """
            SELECT id, target_pet_type, on_sale, sale_starts_on, sale_ends_on, current_plan_version_id
            FROM subscription_plans
            WHERE name = ?
            FOR UPDATE
            """,
            PLAN_NAME);

    if (plans.isEmpty()) {
      createPlan(skuId);
      return;
    }
    if (plans.size() != 1) {
      throw fixtureConflict();
    }
    validateExistingPlan(plans.getFirst(), skuId);
  }

  private long fixtureSkuId() {
    List<Long> skuIds =
        jdbcTemplate.queryForList(
            """
            SELECT sku.id
            FROM skus sku
            JOIN products product ON product.id = sku.product_id
            WHERE product.name = ?
              AND sku.name = ?
            """,
            Long.class,
            LocalQaBootstrapService.PRODUCT_NAME,
            LocalQaBootstrapService.SKU_NAME);
    if (skuIds.size() != 1) {
      throw fixtureConflict();
    }
    return skuIds.getFirst();
  }

  private void createPlan(long skuId) {
    jdbcTemplate.update(
        """
        INSERT INTO subscription_plans(
            name, target_pet_type, on_sale, sale_starts_on, sale_ends_on, current_plan_version_id
        ) VALUES (?, 'DOG', true, NULL, NULL, NULL)
        """,
        PLAN_NAME);
    long planId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

    jdbcTemplate.update(
        "INSERT INTO plan_versions(plan_id, package_price_krw, is_migration_only) VALUES (?, ?,"
            + " false)",
        planId,
        PACKAGE_PRICE_KRW);
    long planVersionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

    jdbcTemplate.update(
        "INSERT INTO plan_items(plan_version_id, sku_id, quantity) VALUES (?, ?, 1)",
        planVersionId,
        skuId);
    for (Integer cycle : DELIVERY_CYCLES) {
      jdbcTemplate.update(
          "INSERT INTO plan_version_delivery_cycles(plan_version_id, delivery_cycle_weeks) VALUES"
              + " (?, ?)",
          planVersionId,
          cycle);
    }
    jdbcTemplate.update(
        "UPDATE subscription_plans SET current_plan_version_id = ? WHERE id = ?",
        planVersionId,
        planId);
  }

  private void validateExistingPlan(Map<String, Object> plan, long skuId) {
    if (!"DOG".equals(plan.get("target_pet_type"))
        || !trueValue(plan.get("on_sale"))
        || plan.get("sale_starts_on") != null
        || plan.get("sale_ends_on") != null
        || !(plan.get("current_plan_version_id") instanceof Number currentVersion)) {
      throw fixtureConflict();
    }

    long planId = ((Number) plan.get("id")).longValue();
    long planVersionId = currentVersion.longValue();
    Integer versionCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM plan_versions
            WHERE id = ?
              AND plan_id = ?
              AND package_price_krw = ?
              AND is_migration_only = false
            """,
            Integer.class,
            planVersionId,
            planId,
            PACKAGE_PRICE_KRW);
    Integer itemCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM plan_items
            WHERE plan_version_id = ?
              AND sku_id = ?
              AND quantity = 1
            """,
            Integer.class,
            planVersionId,
            skuId);
    Integer allItemCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM plan_items WHERE plan_version_id = ?",
            Integer.class,
            planVersionId);
    List<Integer> cycles =
        jdbcTemplate.queryForList(
            """
            SELECT delivery_cycle_weeks
            FROM plan_version_delivery_cycles
            WHERE plan_version_id = ?
            ORDER BY delivery_cycle_weeks
            """,
            Integer.class,
            planVersionId);

    if (versionCount == null
        || versionCount != 1
        || itemCount == null
        || itemCount != 1
        || allItemCount == null
        || allItemCount != 1
        || !DELIVERY_CYCLES.equals(cycles)) {
      throw fixtureConflict();
    }
  }

  private boolean trueValue(Object value) {
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    return value instanceof Number number && number.intValue() == 1;
  }

  private LocalQaBootstrapException fixtureConflict() {
    return new LocalQaBootstrapException("로컬 QA SUBSCRIPTION Plan fixture가 기존 데이터와 충돌합니다.");
  }
}
