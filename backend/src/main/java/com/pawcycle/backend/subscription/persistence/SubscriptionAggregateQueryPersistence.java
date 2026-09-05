package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-side SQL and row mapping for the subscription aggregate boundary. */
class SubscriptionAggregateQueryPersistence {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final JdbcTemplate jdbc;

  SubscriptionAggregateQueryPersistence(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

public PetProjection findOwnedPet(long memberId, long petId) {
    return one(
            "SELECT id,name,pet_type,breed,weight_kg FROM pets WHERE id=? AND member_id=?",
            petId,
            memberId)
        .map(this::pet)
        .orElseThrow(() -> new SubscriptionApiException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다."));
  }

public PlanVersionProjection findPlanVersion(long versionId) {
    return one(
            "SELECT p.id plan_id,p.name"
                + " plan_name,p.current_plan_version_id,p.target_pet_type,p.on_sale,p.sale_starts_on,p.sale_ends_on,v.id"
                + " version_id,v.package_price_krw,v.is_migration_only FROM plan_versions v JOIN"
                + " subscription_plans p ON p.id=v.plan_id WHERE v.id=?",
            versionId)
        .map(this::planVersion)
        .orElseThrow(
            () -> new SubscriptionApiException(404, "PLAN_VERSION_NOT_FOUND", "PlanVersion을 찾을 수 없습니다."));
  }

public boolean deliveryCycleAllowed(long versionId, int cycle) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM plan_version_delivery_cycles WHERE plan_version_id=? AND"
                + " delivery_cycle_weeks=?",
            Integer.class,
            versionId,
            cycle)
        > 0;
  }

public boolean planContainsSku(long versionId, long skuId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM plan_items WHERE plan_version_id=? AND sku_id=?",
            Integer.class,
            versionId,
            skuId)
        > 0;
  }

public boolean scheduleAddonConflicts(long scheduleId, long versionId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedule_addons addon JOIN plan_items item ON"
                + " item.sku_id=addon.sku_id WHERE addon.schedule_id=? AND item.plan_version_id=?",
            Integer.class,
            scheduleId,
            versionId)
        > 0;
  }

public AddonSkuProjection findEligibleAddonSku(long skuId) {
    return one(
            "SELECT sku.id sku_id,product.id product_id,product.name product_name,sku.name"
                + " sku_name,sku.price,product.display_status,category.active"
                + " category_active,brand.active brand_active,sku.status"
                + " sku_status,COALESCE(inventory.available_quantity,0) available_quantity FROM"
                + " skus sku JOIN products product ON product.id=sku.product_id JOIN categories"
                + " category ON category.id=product.category_id JOIN brands brand ON"
                + " brand.id=product.brand_id LEFT JOIN inventories inventory ON"
                + " inventory.sku_id=sku.id WHERE sku.id=?",
            skuId)
        .map(
            row ->
                new AddonSkuProjection(
                    skuId,
                    longValue(row, "product_id"),
                    (String) row.get("product_name"),
                    (String) row.get("sku_name"),
                    (java.math.BigDecimal) row.get("price"),
                    "ACTIVE".equals(row.get("sku_status"))
                        && "PUBLIC".equals(row.get("display_status"))
                        && Boolean.TRUE.equals(row.get("category_active"))
                        && Boolean.TRUE.equals(row.get("brand_active"))
                        && intValue(row, "available_quantity") > 0))
        .orElseThrow(() -> new SubscriptionApiException(404, "ADDON_NOT_FOUND", "Add-on을 찾을 수 없습니다."));
  }

public SubscriptionProjection lockOwnedSubscription(long memberId, long subscriptionId) {
    return one(
            "SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id"
                + " FROM subscriptions WHERE id=? AND member_id=? AND runtime_managed=true FOR UPDATE",
            subscriptionId,
            memberId)
        .map(this::subscription)
        .orElseThrow(
            () -> new SubscriptionApiException(404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다."));
  }

public SubscriptionProjection findOwnedSubscription(long memberId, long subscriptionId) {
    return one(
            "SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id"
                + " FROM subscriptions WHERE id=? AND member_id=? AND runtime_managed=true",
            subscriptionId,
            memberId)
        .map(this::subscription)
        .orElseThrow(
            () -> new SubscriptionApiException(404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다."));
  }

public PageProjection<PetProjection> findPets(long memberId, int page, int size) {
    long total =
        jdbc.queryForObject("SELECT COUNT(*) FROM pets WHERE member_id=?", Long.class, memberId);
    List<PetProjection> items =
        jdbc.query(
            "SELECT id,name,pet_type,breed,weight_kg FROM pets WHERE member_id=? ORDER BY id ASC"
                + " LIMIT ? OFFSET ?",
            (rs, n) ->
                new PetProjection(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("pet_type"),
                    rs.getString("breed"),
                    rs.getBigDecimal("weight_kg")),
            memberId,
            size,
            page * size);
    return new PageProjection<>(page, size, total, items);
  }

public PageProjection<PlanVersionProjection> findSalePlanVersions(
      String petType, LocalDate today, int page, int size) {
    String where =
        " FROM subscription_plans p JOIN plan_versions v ON v.id=p.current_plan_version_id WHERE"
            + " p.name IS NOT NULL AND p.target_pet_type=? AND p.on_sale=true AND"
            + " v.is_migration_only=false AND (p.sale_starts_on IS NULL OR p.sale_starts_on<=?) AND"
            + " (p.sale_ends_on IS NULL OR p.sale_ends_on>=?)";
    long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, petType, today, today);
    List<PlanVersionProjection> items =
        jdbc.query(
            "SELECT p.id plan_id,p.name"
                + " plan_name,p.current_plan_version_id,p.target_pet_type,p.on_sale,p.sale_starts_on,p.sale_ends_on,v.id"
                + " version_id,v.package_price_krw,v.is_migration_only"
                + where
                + " ORDER BY p.id ASC,v.id ASC LIMIT ? OFFSET ?",
            (rs, n) ->
                new PlanVersionProjection(
                    rs.getLong("plan_id"),
                    rs.getString("plan_name"),
                    rs.getObject("current_plan_version_id", Long.class),
                    rs.getString("target_pet_type"),
                    rs.getBoolean("on_sale"),
                    rs.getDate("sale_starts_on") == null
                        ? null
                        : rs.getDate("sale_starts_on").toLocalDate(),
                    rs.getDate("sale_ends_on") == null
                        ? null
                        : rs.getDate("sale_ends_on").toLocalDate(),
                    rs.getLong("version_id"),
                    rs.getLong("package_price_krw"),
                    rs.getBoolean("is_migration_only")),
            petType,
            today,
            today,
            size,
            page * size);
    return new PageProjection<>(page, size, total, items);
  }

public List<SubscriptionItemProjection> findPlanItems(long versionId) {
    return jdbc.query(
        "SELECT sku_id,quantity FROM plan_items WHERE plan_version_id=? ORDER BY sku_id",
        (rs, n) -> new SubscriptionItemProjection(rs.getLong("sku_id"), rs.getInt("quantity")),
        versionId);
  }

public List<Integer> findDeliveryCycles(long versionId) {
    return jdbc.queryForList(
        "SELECT delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id=?"
            + " ORDER BY delivery_cycle_weeks",
        Integer.class,
        versionId);
  }

public Map<Long, List<SubscriptionItemProjection>> findPlanItems(List<Long> versionIds) {
    return groupedItems(
        "SELECT plan_version_id,sku_id,quantity FROM plan_items WHERE plan_version_id IN ",
        versionIds,
        "plan_version_id");
  }

public Map<Long, List<Integer>> findDeliveryCycles(List<Long> versionIds) {
    return groupedIntegers(
        "SELECT plan_version_id,delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE"
            + " plan_version_id IN ",
        versionIds,
        "plan_version_id",
        "delivery_cycle_weeks");
  }

public SubscriptionSnapshot findSnapshot(long snapshotId) {
    SubscriptionSnapshot base =
        one(
                "SELECT id,source_plan_version_id,package_total_krw,delivery_cycle_weeks FROM"
                    + " subscription_snapshots WHERE id=?",
                snapshotId)
            .map(
                row ->
                    new SubscriptionSnapshot(
                        longValue(row, "id"),
                        longValue(row, "source_plan_version_id"),
                        longValue(row, "package_total_krw"),
                        intValue(row, "delivery_cycle_weeks"),
                        List.of()))
            .orElseThrow();
    return new SubscriptionSnapshot(
        base.id(),
        base.planVersionId(),
        base.packagePriceKrw(),
        base.deliveryCycleWeeks(),
        findPlanItemsForSnapshot(snapshotId));
  }

private List<SubscriptionItemProjection> findPlanItemsForSnapshot(long snapshotId) {
    return jdbc.query(
        "SELECT sku_id,quantity FROM subscription_snapshot_items WHERE snapshot_id=? ORDER BY"
            + " sku_id",
        (rs, n) -> new SubscriptionItemProjection(rs.getLong("sku_id"), rs.getInt("quantity")),
        snapshotId);
  }

public ScheduleProjection lockNextScheduled(long subscriptionId) {
    return one(
            "SELECT schedule.id,schedule.scheduled_date FROM subscription_schedules schedule LEFT"
                + " JOIN subscription_orders existing_order ON"
                + " existing_order.schedule_id=schedule.id WHERE schedule.subscription_id=? AND"
                + " (schedule.status='SCHEDULED' OR (schedule.status='HELD' AND"
                + " schedule.hold_reason='ORDER_STOCK_UNAVAILABLE')) AND existing_order.id IS NULL"
                + " ORDER BY schedule.scheduled_date,schedule.id LIMIT 1 FOR UPDATE",
            subscriptionId)
        .map(
            row ->
                new ScheduleProjection(
                    longValue(row, "id"), date(row.get("scheduled_date"))))
        .orElseThrow(
            () ->
                new SubscriptionApiException(409, "SUBSCRIPTION_COMMAND_NOT_ALLOWED", "다음 Schedule이 없습니다."));
  }

public Optional<PendingSubscriptionChange> findPendingChange(long subscriptionId) {
    return one(
            "SELECT pending.snapshot_id,pending.target_schedule_id,schedule.scheduled_date FROM"
                + " pending_plan_changes pending JOIN subscription_schedules schedule ON"
                + " schedule.id=pending.target_schedule_id WHERE pending.subscription_id=?",
            subscriptionId)
        .map(
            row ->
                new PendingSubscriptionChange(
                    longValue(row, "snapshot_id"),
                    longValue(row, "target_schedule_id"),
                    date(row.get("scheduled_date"))));
  }

public int scheduleAddonCount(long scheduleId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM subscription_schedule_addons WHERE schedule_id=?",
        Integer.class,
        scheduleId);
  }

public boolean hasScheduleAddon(long scheduleId, long skuId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedule_addons WHERE schedule_id=? AND sku_id=?",
            Integer.class,
            scheduleId,
            skuId)
        > 0;
  }

public List<ScheduleAddonProjection> findScheduleAddons(long scheduleId) {
    return jdbc.query(
        "SELECT addon.schedule_id,addon.sku_id,sku.product_id,product.name product_name,sku.name"
            + " sku_name,addon.quantity,addon.unit_price_krw FROM subscription_schedule_addons"
            + " addon JOIN skus sku ON sku.id=addon.sku_id JOIN products product ON"
            + " product.id=sku.product_id WHERE addon.schedule_id=? ORDER BY addon.sku_id",
        (rs, n) ->
            new ScheduleAddonProjection(
                rs.getLong(1),
                rs.getLong(2),
                rs.getLong(3),
                rs.getString(4),
                rs.getString(5),
                rs.getInt(6),
                rs.getBigDecimal(7)),
        scheduleId);
  }

public boolean scheduleDateTaken(long subscriptionId, LocalDate date, long excludedScheduleId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                + " scheduled_date=? AND id<>?",
            Integer.class,
            subscriptionId,
            date,
            excludedScheduleId)
        > 0;
  }

private PetProjection pet(Map<String, Object> row) {
    return new PetProjection(
        longValue(row, "id"),
        (String) row.get("name"),
        (String) row.get("pet_type"),
        (String) row.get("breed"),
        (java.math.BigDecimal) row.get("weight_kg"));
  }

private PlanVersionProjection planVersion(Map<String, Object> row) {
    return new PlanVersionProjection(
        longValue(row, "plan_id"),
        (String) row.get("plan_name"),
        row.get("current_plan_version_id") == null
            ? null
            : longValue(row, "current_plan_version_id"),
        (String) row.get("target_pet_type"),
        Boolean.TRUE.equals(row.get("on_sale")),
        date(row.get("sale_starts_on")),
        date(row.get("sale_ends_on")),
        longValue(row, "version_id"),
        longValue(row, "package_price_krw"),
        Boolean.TRUE.equals(row.get("is_migration_only")));
  }

private SubscriptionProjection subscription(Map<String, Object> row) {
    return new SubscriptionProjection(
        longValue(row, "id"),
        longValue(row, "member_id"),
        (String) row.get("status"),
        longValue(row, "version"),
        row.get("pet_id") == null ? null : longValue(row, "pet_id"),
        intValue(row, "delivery_cycle_weeks"),
        longValue(row, "current_snapshot_id"));
  }

private LocalDate date(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDate localDate) return localDate;
    if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime().toLocalDate();
    }
    throw new IllegalArgumentException("Unsupported date value type: " + value.getClass().getName());
  }

public PageProjection<SubscriptionProjection> findSubscriptions(
      long memberId, int page, int size) {
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscriptions WHERE member_id=? AND runtime_managed=true",
            Long.class,
            memberId);
    List<SubscriptionProjection> items =
        jdbc.query(
            "SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id"
                + " FROM subscriptions WHERE member_id=? AND runtime_managed=true ORDER BY id DESC"
                + " LIMIT ? OFFSET ?",
            (rs, n) ->
                new SubscriptionProjection(
                    rs.getLong("id"),
                    rs.getLong("member_id"),
                    rs.getString("status"),
                    rs.getLong("version"),
                    rs.getObject("pet_id", Long.class),
                    rs.getInt("delivery_cycle_weeks"),
                    rs.getLong("current_snapshot_id")),
            memberId,
            size,
            page * size);
    return new PageProjection<>(page, size, total, items);
  }

public Map<Long, PetProjection> findOwnedPets(long memberId, List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, PetProjection> result = new HashMap<>();
    jdbc.query(
        "SELECT id,name,pet_type,breed,weight_kg FROM pets WHERE member_id=? AND id IN "
            + placeholders(ids.size()),
        rs -> {
          result.put(
              rs.getLong("id"),
              new PetProjection(
                  rs.getLong("id"),
                  rs.getString("name"),
                  rs.getString("pet_type"),
                  rs.getString("breed"),
                  rs.getBigDecimal("weight_kg")));
        },
        withLeading(memberId, ids));
    return result;
  }

public Map<Long, SubscriptionSnapshotBase> findSnapshots(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, SubscriptionSnapshotBase> result = new HashMap<>();
    jdbc.query(
        "SELECT id,source_plan_version_id,package_total_krw,delivery_cycle_weeks FROM"
            + " subscription_snapshots WHERE id IN "
            + placeholders(ids.size()),
        rs -> {
          result.put(
              rs.getLong("id"),
              new SubscriptionSnapshotBase(
                  rs.getLong("id"),
                  rs.getLong("source_plan_version_id"),
                  rs.getLong("package_total_krw"),
                  rs.getInt("delivery_cycle_weeks")));
        },
        ids.toArray());
    return result;
  }

public Map<Long, List<SubscriptionItemProjection>> findSnapshotItems(List<Long> snapshotIds) {
    return groupedItems(
        "SELECT snapshot_id,sku_id,quantity FROM subscription_snapshot_items WHERE snapshot_id IN ",
        snapshotIds,
        "snapshot_id");
  }

public Map<Long, LocalDate> findNextSchedules(List<Long> subscriptionIds, LocalDate today) {
    if (subscriptionIds.isEmpty()) return Map.of();
    Map<Long, LocalDate> result = new HashMap<>();
    jdbc.query(
        "SELECT schedule.subscription_id,schedule.scheduled_date FROM subscription_schedules"
            + " schedule LEFT JOIN subscription_orders existing_order ON"
            + " existing_order.schedule_id=schedule.id WHERE schedule.subscription_id IN "
            + placeholders(subscriptionIds.size())
            + " AND schedule.status='SCHEDULED' AND schedule.scheduled_date>=? AND"
            + " existing_order.id IS NULL ORDER BY"
            + " schedule.subscription_id,schedule.scheduled_date,schedule.id",
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs ->
                result.putIfAbsent(
                    rs.getLong("subscription_id"), rs.getDate("scheduled_date").toLocalDate()),
        withLast(subscriptionIds, today));
    return result;
  }

public Optional<LocalDate> findNextSchedule(long subscriptionId, LocalDate today) {
    return jdbc.query(
        "SELECT schedule.scheduled_date FROM subscription_schedules schedule LEFT JOIN"
            + " subscription_orders existing_order ON existing_order.schedule_id=schedule.id WHERE"
            + " schedule.subscription_id=? AND schedule.status='SCHEDULED' AND"
            + " schedule.scheduled_date>=? AND existing_order.id IS NULL ORDER BY"
            + " schedule.scheduled_date,schedule.id LIMIT 1",
        rs -> rs.next() ? Optional.of(rs.getDate(1).toLocalDate()) : Optional.empty(),
        subscriptionId,
        today);
  }

public Optional<Long> findPendingSnapshotId(long subscriptionId) {
    return one(
            "SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?", subscriptionId)
        .map(row -> longValue(row, "snapshot_id"));
  }

public Optional<NextDeliveryProjection> findNextDeliverySchedule(long subscriptionId) {
    return one(
            "SELECT"
                + " schedule.id,schedule.scheduled_date,schedule.status,schedule.hold_reason,schedule.effective_snapshot_id"
                + " FROM subscription_schedules schedule WHERE schedule.subscription_id=? AND"
                + " (schedule.status='HELD' OR (schedule.status='SCHEDULED' AND NOT EXISTS (SELECT"
                + " 1 FROM subscription_orders existing_order WHERE"
                + " existing_order.schedule_id=schedule.id))) ORDER BY"
                + " schedule.scheduled_date,schedule.id LIMIT 1",
            subscriptionId)
        .map(
            row ->
                new NextDeliveryProjection(
                    longValue(row, "id"),
                    date(row.get("scheduled_date")),
                    (String) row.get("status"),
                    (String) row.get("hold_reason"),
                    row.get("effective_snapshot_id") == null
                        ? null
                        : longValue(row, "effective_snapshot_id")));
  }

public List<SubscriptionItemDetailProjection> findSnapshotItemDetails(long snapshotId) {
    return jdbc.query(
        "SELECT item.sku_id,sku.name sku_name,product.id product_id,product.name"
            + " product_name,product.thumbnail_url,item.quantity FROM subscription_snapshot_items"
            + " item JOIN skus sku ON sku.id=item.sku_id JOIN products product ON"
            + " product.id=sku.product_id WHERE item.snapshot_id=? ORDER BY item.sku_id",
        (rs, n) ->
            new SubscriptionItemDetailProjection(
                rs.getLong("sku_id"),
                rs.getString("sku_name"),
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("thumbnail_url"),
                rs.getInt("quantity")),
        snapshotId);
  }

public PageProjection<ScheduleViewProjection> findScheduleViews(
      long subscriptionId, int page, int size) {
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=?",
            Long.class,
            subscriptionId);
    List<ScheduleViewProjection> items =
        jdbc.query(
            "SELECT id,scheduled_date,status,effective_snapshot_id FROM subscription_schedules"
                + " WHERE subscription_id=? ORDER BY scheduled_date DESC,id DESC LIMIT ? OFFSET ?",
            (rs, n) ->
                new ScheduleViewProjection(
                    rs.getLong("id"),
                    rs.getDate("scheduled_date").toLocalDate(),
                    rs.getString("status"),
                    rs.getObject("effective_snapshot_id", Long.class)),
            subscriptionId,
            size,
            page * size);
    return new PageProjection<>(page, size, total, items);
  }

public PageProjection<CommandHistoryProjection> findCommandHistory(
      long subscriptionId, int page, int size) {
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_command_history WHERE subscription_id=?",
            Long.class,
            subscriptionId);
    List<CommandHistoryProjection> items =
        jdbc.query(
            "SELECT command_type,occurred_at FROM subscription_command_history WHERE"
                + " subscription_id=? ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
            (rs, n) ->
                new CommandHistoryProjection(
                    rs.getString("command_type"),
                    rs.getTimestamp("occurred_at")
                        .toInstant()
                        .atZone(SEOUL)
                        .toOffsetDateTime()
                        .toString()),
            subscriptionId,
            size,
            page * size);
    return new PageProjection<>(page, size, total, items);
  }

public List<Long> activeSubscriptionIds() {
    return jdbc.queryForList(
        "SELECT id FROM subscriptions WHERE runtime_managed=true AND status='ACTIVE' ORDER BY id",
        Long.class);
  }

public Optional<SubscriptionProjection> lockActiveSubscription(long subscriptionId) {
    return one(
            "SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id"
                + " FROM subscriptions WHERE id=? AND runtime_managed=true AND status='ACTIVE' FOR"
                + " UPDATE",
            subscriptionId)
        .map(this::subscription);
  }

public boolean hasUnprocessedDueSchedule(long subscriptionId, LocalDate today) {
    return !jdbc.queryForList(
            "SELECT schedule.id FROM subscription_schedules schedule LEFT JOIN subscription_orders"
                + " existing_order ON existing_order.schedule_id=schedule.id WHERE"
                + " schedule.subscription_id=? AND schedule.status='SCHEDULED' AND"
                + " schedule.scheduled_date<=? AND existing_order.id IS NULL ORDER BY"
                + " schedule.scheduled_date,schedule.id LIMIT 1 FOR UPDATE",
            subscriptionId,
            today)
        .isEmpty();
  }

public List<ScheduleProjection> futureSchedulesForUpdate(long subscriptionId, LocalDate today) {
    return jdbc.query(
        "SELECT id,scheduled_date FROM subscription_schedules WHERE subscription_id=? AND"
            + " status='SCHEDULED' AND scheduled_date>? ORDER BY scheduled_date,id FOR UPDATE",
        (rs, rowNum) ->
            new ScheduleProjection(
                rs.getLong("id"), rs.getDate("scheduled_date").toLocalDate()),
        subscriptionId,
        today);
  }

public Optional<ProcessedScheduleProjection> lastProcessedSchedule(long subscriptionId) {
    return one(
            "SELECT orders.scheduled_date,snapshot.delivery_cycle_weeks FROM subscription_orders"
                + " orders JOIN subscription_snapshots snapshot ON"
                + " snapshot.id=orders.effective_snapshot_id WHERE orders.subscription_id=? ORDER"
                + " BY orders.scheduled_date DESC,orders.id DESC LIMIT 1",
            subscriptionId)
        .map(
            row ->
                new ProcessedScheduleProjection(
                    date(row.get("scheduled_date")), intValue(row, "delivery_cycle_weeks")));
  }

public boolean scheduleExists(long subscriptionId, LocalDate date) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                + " scheduled_date=?",
            Integer.class,
            subscriptionId,
            date)
        > 0;
  }

private Map<Long, List<SubscriptionItemProjection>> groupedItems(
      String prefix, List<Long> ids, String key) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, List<SubscriptionItemProjection>> result = new HashMap<>();
    jdbc.query(
        prefix + placeholders(ids.size()) + " ORDER BY " + key + ",sku_id",
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs ->
                result
                    .computeIfAbsent(rs.getLong(key), ignored -> new ArrayList<>())
                    .add(new SubscriptionItemProjection(rs.getLong("sku_id"), rs.getInt("quantity"))),
        ids.toArray());
    return result;
  }

private Map<Long, List<Integer>> groupedIntegers(
      String prefix, List<Long> ids, String key, String value) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, List<Integer>> result = new HashMap<>();
    jdbc.query(
        prefix + placeholders(ids.size()) + " ORDER BY " + key + "," + value,
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs ->
                result
                    .computeIfAbsent(rs.getLong(key), ignored -> new ArrayList<>())
                    .add(rs.getInt(value)),
        ids.toArray());
    return result;
  }

private Object[] withLast(List<Long> ids, Object last) {
    List<Object> args = new ArrayList<>(ids);
    args.add(last);
    return args.toArray();
  }

private String placeholders(int count) {
    return "(" + String.join(",", Collections.nCopies(count, "?")) + ")";
  }

private Optional<Map<String, Object>> one(String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

private long longValue(Map<String, Object> r, String k) {
    return ((Number) r.get(k)).longValue();
  }

private int intValue(Map<String, Object> r, String k) {
    return ((Number) r.get(k)).intValue();
  }

private Object[] withLeading(Object leading, List<Long> ids) {
    List<Object> args = new ArrayList<>();
    args.add(leading);
    args.addAll(ids);
    return args.toArray();
  }
}
