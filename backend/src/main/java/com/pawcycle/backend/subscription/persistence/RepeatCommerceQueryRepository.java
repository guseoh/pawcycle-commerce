package com.pawcycle.backend.subscription.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class RepeatCommerceQueryRepository {
  private final JdbcTemplate jdbc;

  public RepeatCommerceQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<PurchaseRow> findOneTimePurchaseDates(long memberId) {
    List<PurchaseRow> rows = new ArrayList<>();
    jdbc.query(
        "SELECT p.id,p.name,DATE(o.paid_at) purchased_date FROM orders o JOIN payments pay ON"
            + " pay.order_id=o.id AND pay.status='SUCCEEDED' JOIN order_items oi ON"
            + " oi.order_id=o.id JOIN skus sku ON sku.id=oi.sku_id JOIN products p ON"
            + " p.id=sku.product_id WHERE o.member_id=? AND o.source='ONE_TIME' AND o.status='PAID'"
            + " AND o.paid_at IS NOT NULL GROUP BY o.id,p.id,p.name,o.paid_at ORDER BY p.id,"
            + " o.paid_at DESC, o.id DESC",
        (RowCallbackHandler)
            rs ->
                rows.add(
                    new PurchaseRow(rs.getLong(1), rs.getString(2), rs.getDate(3).toLocalDate())),
        memberId);
    return rows;
  }

  public Optional<SubscriptionRow> findRuntimeSubscription(long memberId, long subscriptionId) {
    return jdbc.query(
            "SELECT id,status,delivery_cycle_weeks,current_snapshot_id FROM subscriptions WHERE"
                + " id=? AND member_id=? AND runtime_managed=true",
            (rs, rowNum) ->
                new SubscriptionRow(
                    rs.getLong("id"),
                    rs.getString("status"),
                    rs.getInt("delivery_cycle_weeks"),
                    rs.getLong("current_snapshot_id")),
            subscriptionId,
            memberId)
        .stream()
        .findFirst();
  }

  public List<java.time.LocalDate> findSuccessfulScheduleDates(long subscriptionId) {
    return jdbc.query(
        "SELECT context.scheduled_date FROM subscription_order_context context JOIN orders o ON"
            + " o.id=context.order_id JOIN payments pay ON pay.order_id=o.id AND"
            + " pay.status='SUCCEEDED' WHERE context.subscription_id=? AND"
            + " o.source='SUBSCRIPTION' AND o.status='PAID' AND context.scheduled_date IS NOT"
            + " NULL ORDER BY context.scheduled_date DESC,context.order_id DESC",
        (rs, rowNum) -> rs.getDate(1).toLocalDate(),
        subscriptionId);
  }

  public long findSourcePlanVersionId(long snapshotId) {
    Long value =
        jdbc.queryForObject(
            "SELECT source_plan_version_id FROM subscription_snapshots WHERE id=?",
            Long.class,
            snapshotId);
    return value;
  }

  public List<Integer> findAllowedDeliveryCycles(long planVersionId) {
    return jdbc.queryForList(
        "SELECT delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id=?"
            + " ORDER BY delivery_cycle_weeks",
        Integer.class,
        planVersionId);
  }

  public Optional<Long> findPaidOneTimeOrder(long memberId, long orderId) {
    return jdbc.query(
            "SELECT id FROM orders o WHERE o.id=? AND o.member_id=? AND o.source='ONE_TIME' AND"
                + " o.status='PAID' AND EXISTS (SELECT 1 FROM payments p WHERE p.order_id=o.id AND"
                + " p.status='SUCCEEDED')",
            (rs, rowNum) -> rs.getLong(1),
            orderId,
            memberId)
        .stream()
        .findFirst();
  }

  public Set<Long> findOrderProductIds(long orderId) {
    return new LinkedHashSet<>(
        jdbc.queryForList(
            "SELECT DISTINCT sku.product_id FROM order_items item JOIN skus sku ON"
                + " sku.id=item.sku_id WHERE item.order_id=?",
            Long.class,
            orderId));
  }

  public List<PlanOptionRow> findAvailablePlanOptions(java.time.LocalDate today, long memberId) {
    List<PlanOptionRow> options = new ArrayList<>();
    List<PlanVersionRow> versions =
        jdbc.query(
            "SELECT v.id version_id,p.name plan_name,p.target_pet_type,v.package_price_krw FROM"
                + " plan_versions v JOIN subscription_plans p ON p.id=v.plan_id WHERE"
                + " p.current_plan_version_id=v.id AND p.name IS NOT NULL AND p.on_sale=true AND"
                + " v.is_migration_only=false AND (p.sale_starts_on IS NULL OR p.sale_starts_on<=?)"
                + " AND (p.sale_ends_on IS NULL OR p.sale_ends_on>=?) ORDER BY v.id",
            (rs, rowNum) ->
                new PlanVersionRow(
                    rs.getLong("version_id"),
                    rs.getString("plan_name"),
                    rs.getString("target_pet_type"),
                    rs.getBigDecimal("package_price_krw")),
            today,
            today);
    for (PlanVersionRow version : versions) {
      List<Long> products =
          jdbc.queryForList(
              "SELECT DISTINCT sku.product_id FROM plan_items item JOIN skus sku ON"
                  + " sku.id=item.sku_id WHERE item.plan_version_id=?",
              Long.class,
              version.planVersionId());
      List<Long> pets =
          jdbc.queryForList(
              "SELECT id FROM pets WHERE member_id=? AND pet_type=? ORDER BY id",
              Long.class,
              memberId,
              version.targetPetType());
      options.add(
          new PlanOptionRow(
              version.planVersionId(),
              version.planName(),
              version.packagePriceKrw(),
              products,
              pets,
              findAllowedDeliveryCycles(version.planVersionId())));
    }
    return options;
  }

  public record PurchaseRow(long productId, String productName, java.time.LocalDate purchasedDate) {}

  public record SubscriptionRow(
      long id, String status, int deliveryCycleWeeks, long currentSnapshotId) {}

  public record PlanOptionRow(
      long planVersionId,
      String planName,
      BigDecimal packagePriceKrw,
      List<Long> productIds,
      List<Long> petIds,
      List<Integer> allowedDeliveryCycleWeeks) {}

  private record PlanVersionRow(
      long planVersionId, String planName, String targetPetType, BigDecimal packagePriceKrw) {}
}
