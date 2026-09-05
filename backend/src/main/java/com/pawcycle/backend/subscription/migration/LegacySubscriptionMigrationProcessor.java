package com.pawcycle.backend.subscription.migration;

import com.pawcycle.backend.subscription.LegacySubscriptionPreflight;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately not an ApplicationRunner. An operator must first establish the separate approved
 * source-write freeze boundary; this local service then keeps preflight and all DML in one call.
 */
@Service
public class LegacySubscriptionMigrationProcessor {
  private static final long JSON_SAFE_MAX = 9_007_199_254_740_991L;
  private final JdbcTemplate jdbc;

  public LegacySubscriptionMigrationProcessor(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public LegacySubscriptionPreflight preflight() {
    List<Map<String, Object>> invalid =
        jdbc.queryForList(
            """
            SELECT s.id, s.quantity, s.delivery_cycle_weeks, sk.price, p.pet_type
            FROM subscriptions s JOIN skus sk ON sk.id=s.sku_id JOIN products p ON p.id=sk.product_id
            WHERE s.runtime_managed=false
              AND (s.quantity < 1 OR s.delivery_cycle_weeks NOT IN (2,4,8)
                   OR p.pet_type IS NULL OR TRIM(UPPER(p.pet_type)) NOT IN ('DOG','CAT')
                   OR sk.price < 0 OR sk.price <> FLOOR(sk.price)
                   OR CAST(sk.price AS DECIMAL(30,0)) * s.quantity > ?)
            """,
            JSON_SAFE_MAX);
    return new LegacySubscriptionPreflight(invalid.isEmpty(), invalid.size());
  }

  /** All-or-nothing DML phase. The caller must enforce the external application/DB write freeze. */
  @Transactional
  public void migrateAfterSourceWriteFreeze(boolean sourceWritesFrozen) {
    if (!sourceWritesFrozen)
      throw new IllegalStateException("legacy source writes must be frozen before migration");
    LegacySubscriptionPreflight preflight = preflight();
    if (!preflight.valid())
      throw new IllegalStateException("legacy preflight failed; no migration DML was written");
    List<Map<String, Object>> legacy =
        jdbc.queryForList(
            """
            SELECT s.id subscription_id,s.member_id,s.sku_id,s.quantity,s.delivery_cycle_weeks,
                   s.next_order_date,sk.price,TRIM(UPPER(p.pet_type)) pet_type
            FROM subscriptions s JOIN skus sk ON sk.id=s.sku_id JOIN products p ON p.id=sk.product_id
            WHERE s.runtime_managed=false FOR UPDATE
            """);
    for (Map<String, Object> row : legacy) migrateOne(row);
  }

  private void migrateOne(Map<String, Object> row) {
    long subscriptionId = ((Number) row.get("subscription_id")).longValue();
    long skuId = ((Number) row.get("sku_id")).longValue();
    int quantity = ((Number) row.get("quantity")).intValue();
    int cycle = ((Number) row.get("delivery_cycle_weeks")).intValue();
    long total = Math.multiplyExact(((BigDecimal) row.get("price")).longValueExact(), quantity);
    jdbc.update(
        "INSERT INTO subscription_plans(target_pet_type,on_sale,current_plan_version_id) VALUES"
            + " (?,false,NULL)",
        row.get("pet_type"));
    long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,?,true)",
        planId,
        total);
    long versionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,?)",
        versionId,
        skuId,
        quantity);
    jdbc.update(
        "INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES"
            + " (?,?)",
        versionId,
        cycle);
    jdbc.update(
        "UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", versionId, planId);
    jdbc.update(
        "INSERT INTO"
            + " subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks)"
            + " VALUES (?,?,?,?)",
        subscriptionId,
        versionId,
        total,
        cycle);
    long snapshotId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) VALUES (?,?,?)",
        snapshotId,
        skuId,
        quantity);
    LocalDate scheduled = date(row.get("next_order_date"));
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    while (scheduled.isBefore(today)) scheduled = scheduled.plusWeeks(cycle);
    jdbc.update(
        "UPDATE subscriptions SET"
            + " status='ACTIVE',version=0,current_snapshot_id=?,legacy_api_visible=false,runtime_managed=true"
            + " WHERE id=?",
        snapshotId,
        subscriptionId);
    jdbc.update(
        "INSERT INTO"
            + " subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id)"
            + " VALUES (?,?,'SCHEDULED',NULL)",
        subscriptionId,
        scheduled);
  }

  private static LocalDate date(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDate localDate) return localDate;
    if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime().toLocalDate();
    }
    throw new IllegalArgumentException("Unsupported date value type: " + value.getClass().getName());
  }

}
