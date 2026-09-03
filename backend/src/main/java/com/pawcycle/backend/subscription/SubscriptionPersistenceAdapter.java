package com.pawcycle.backend.subscription;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import com.pawcycle.backend.foundation.persistence.PersistenceExceptionClassifier;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Subscription persistence currently keeps multi-table aggregate and compare-and-set writes explicit.
 */
@Component
class SubscriptionPersistenceAdapter {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final NativeQueryExecutor jdbc;

  SubscriptionPersistenceAdapter(NativeQueryExecutor jdbc) {
    this.jdbc = jdbc;
  }

  PetProjection findOwnedPet(long memberId, long petId) {
    return one(
            "SELECT id,name,pet_type,breed,weight_kg FROM pets WHERE id=? AND member_id=?",
            petId,
            memberId)
        .map(this::pet)
        .orElseThrow(() -> new SubscriptionApiException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다."));
  }

  PlanVersionProjection findPlanVersion(long versionId) {
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

  boolean deliveryCycleAllowed(long versionId, int cycle) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM plan_version_delivery_cycles WHERE plan_version_id=? AND"
                + " delivery_cycle_weeks=?",
            Integer.class,
            versionId,
            cycle)
        > 0;
  }

  boolean planContainsSku(long versionId, long skuId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM plan_items WHERE plan_version_id=? AND sku_id=?",
            Integer.class,
            versionId,
            skuId)
        > 0;
  }

  boolean scheduleAddonConflicts(long scheduleId, long versionId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedule_addons addon JOIN plan_items item ON"
                + " item.sku_id=addon.sku_id WHERE addon.schedule_id=? AND item.plan_version_id=?",
            Integer.class,
            scheduleId,
            versionId)
        > 0;
  }

  AddonSkuProjection findEligibleAddonSku(long skuId) {
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

  boolean reserveCreation(long memberId, String key, String fingerprint) {
    try {
      jdbc.update(
          "INSERT INTO"
              + " subscription_creation_idempotency_results(member_id,idempotency_key,payload_fingerprint)"
              + " VALUES (?,?,?)",
          memberId,
          key,
          fingerprint);
      return true;
    } catch (RuntimeException exception) {
      if (PersistenceExceptionClassifier.isDuplicateKey(exception)) return false;
      throw exception;
    }
  }

  StoredIdempotencyResult lockCreationResult(long memberId, String key) {
    return one(
            "SELECT payload_fingerprint,response_status,response_body,location_header,etag_header"
                + " FROM subscription_creation_idempotency_results WHERE member_id=? AND"
                + " idempotency_key=? FOR UPDATE",
            memberId,
            key)
        .map(this::storedIdempotency)
        .orElseThrow();
  }

  void updateCreationResponse(
      long memberId,
      String key,
      long subscriptionId,
      SubscriptionOperationResult result,
      String bodyJson) {
    jdbc.update(
        "UPDATE subscription_creation_idempotency_results SET"
            + " subscription_id=?,response_status=?,response_body=?,location_header=?,etag_header=?,completed_at=COALESCE(completed_at,UTC_TIMESTAMP(6))"
            + " WHERE member_id=? AND idempotency_key=?",
        subscriptionId,
        result.status(),
        bodyJson,
        result.location(),
        result.etag(),
        memberId,
        key);
  }

  void updateStoredCreationBody(long memberId, String key, String bodyJson) {
    jdbc.update(
        "UPDATE subscription_creation_idempotency_results SET response_body=? WHERE member_id=? AND"
            + " idempotency_key=?",
        bodyJson,
        memberId,
        key);
  }

  long insertSubscription(
      long memberId, long versionId, int cycle, long petId, LocalDate created, LocalDate next) {
    jdbc.update(
        "INSERT INTO"
            + " subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,pet_id,status,version,current_snapshot_id,legacy_api_visible,runtime_managed)"
            + " VALUES (?,?,?,?,?,?,?,'ACTIVE',0,NULL,false,true)",
        memberId,
        firstSku(versionId),
        1,
        cycle,
        created,
        next,
        petId);
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  void setCurrentSnapshot(long subscriptionId, long snapshotId) {
    jdbc.update(
        "UPDATE subscriptions SET current_snapshot_id=? WHERE id=?", snapshotId, subscriptionId);
  }

  void insertScheduled(long subscriptionId, LocalDate date) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id)"
            + " VALUES (?,?,'SCHEDULED',NULL)",
        subscriptionId,
        date);
  }

  long createSnapshot(long subscriptionId, long versionId, int cycle, long price) {
    return snapshot(subscriptionId, versionId, cycle, price);
  }

  SubscriptionProjection lockOwnedSubscription(long memberId, long subscriptionId) {
    return one(
            "SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id"
                + " FROM subscriptions WHERE id=? AND member_id=? AND runtime_managed=true FOR UPDATE",
            subscriptionId,
            memberId)
        .map(this::subscription)
        .orElseThrow(
            () -> new SubscriptionApiException(404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다."));
  }

  SubscriptionProjection findOwnedSubscription(long memberId, long subscriptionId) {
    return one(
            "SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id"
                + " FROM subscriptions WHERE id=? AND member_id=? AND runtime_managed=true",
            subscriptionId,
            memberId)
        .map(this::subscription)
        .orElseThrow(
            () -> new SubscriptionApiException(404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다."));
  }

  long insertPet(long memberId, String name, String petType) {
    jdbc.update(
        "INSERT INTO pets(member_id,name,pet_type) VALUES (?,?,?)", memberId, name, petType);
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  void updatePet(
      long memberId,
      long petId,
      String name,
      boolean namePresent,
      String breed,
      boolean breedPresent,
      java.math.BigDecimal weightKg,
      boolean weightPresent) {
    List<String> columns = new ArrayList<>();
    List<Object> arguments = new ArrayList<>();
    if (namePresent) {
      columns.add("name=?");
      arguments.add(name);
    }
    if (breedPresent) {
      columns.add("breed=?");
      arguments.add(breed);
    }
    if (weightPresent) {
      columns.add("weight_kg=?");
      arguments.add(weightKg);
    }
    arguments.add(petId);
    arguments.add(memberId);
    if (jdbc.update(
            "UPDATE pets SET " + String.join(",", columns) + " WHERE id=? AND member_id=?",
            arguments.toArray())
        != 1) throw new SubscriptionApiException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다.");
  }

  PageProjection<PetProjection> findPets(long memberId, int page, int size) {
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

  PageProjection<PlanVersionProjection> findSalePlanVersions(
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

  List<SubscriptionItemProjection> findPlanItems(long versionId) {
    return jdbc.query(
        "SELECT sku_id,quantity FROM plan_items WHERE plan_version_id=? ORDER BY sku_id",
        (rs, n) -> new SubscriptionItemProjection(rs.getLong("sku_id"), rs.getInt("quantity")),
        versionId);
  }

  List<Integer> findDeliveryCycles(long versionId) {
    return jdbc.queryForList(
        "SELECT delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id=?"
            + " ORDER BY delivery_cycle_weeks",
        Integer.class,
        versionId);
  }

  Map<Long, List<SubscriptionItemProjection>> findPlanItems(List<Long> versionIds) {
    return groupedItems(
        "SELECT plan_version_id,sku_id,quantity FROM plan_items WHERE plan_version_id IN ",
        versionIds,
        "plan_version_id");
  }

  Map<Long, List<Integer>> findDeliveryCycles(List<Long> versionIds) {
    return groupedIntegers(
        "SELECT plan_version_id,delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE"
            + " plan_version_id IN ",
        versionIds,
        "plan_version_id",
        "delivery_cycle_weeks");
  }

  SubscriptionSnapshot findSnapshot(long snapshotId) {
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

  boolean reserveCommand(
      long memberId, long subscriptionId, String command, String key, String fingerprint) {
    try {
      jdbc.update(
          "INSERT INTO"
              + " subscription_command_idempotency_results(member_id,subscription_id,command_type,idempotency_key,payload_fingerprint)"
              + " VALUES (?,?,?,?,?)",
          memberId,
          subscriptionId,
          command,
          key,
          fingerprint);
      return true;
    } catch (RuntimeException exception) {
      if (PersistenceExceptionClassifier.isDuplicateKey(exception)) return false;
      throw exception;
    }
  }

  StoredIdempotencyResult lockCommandResult(
      long memberId, long subscriptionId, String command, String key) {
    return one(
            "SELECT payload_fingerprint,response_status,response_body,location_header,etag_header"
                + " FROM subscription_command_idempotency_results WHERE member_id=? AND"
                + " subscription_id=? AND command_type=? AND idempotency_key=? FOR UPDATE",
            memberId,
            subscriptionId,
            command,
            key)
        .map(this::storedIdempotency)
        .orElseThrow();
  }

  void updateCommandResponse(
      long memberId,
      long subscriptionId,
      String command,
      String key,
      SubscriptionOperationResult result,
      String bodyJson) {
    jdbc.update(
        "UPDATE subscription_command_idempotency_results SET"
            + " response_status=?,response_body=?,location_header=?,etag_header=?,completed_at=COALESCE(completed_at,UTC_TIMESTAMP(6))"
            + " WHERE member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=?",
        result.status(),
        bodyJson,
        result.location(),
        result.etag(),
        memberId,
        subscriptionId,
        command,
        key);
  }

  void updateStoredCommandBody(
      long memberId, long subscriptionId, String command, String key, String bodyJson) {
    jdbc.update(
        "UPDATE subscription_command_idempotency_results SET response_body=? WHERE member_id=? AND"
            + " subscription_id=? AND command_type=? AND idempotency_key=?",
        bodyJson,
        memberId,
        subscriptionId,
        command,
        key);
  }

  ScheduleProjection lockNextScheduled(long subscriptionId) {
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

  void replacePendingPlanChange(long subscriptionId, long snapshotId, long scheduleId) {
    jdbc.update("DELETE FROM pending_plan_changes WHERE subscription_id=?", subscriptionId);
    jdbc.update(
        "INSERT INTO pending_plan_changes(subscription_id,snapshot_id,target_schedule_id) VALUES"
            + " (?,?,?)",
        subscriptionId,
        snapshotId,
        scheduleId);
  }

  Optional<PendingSubscriptionChange> findPendingChange(long subscriptionId) {
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

  void setSubscriptionPet(long subscriptionId, long petId) {
    jdbc.update("UPDATE subscriptions SET pet_id=? WHERE id=?", petId, subscriptionId);
  }

  void markSkipped(long scheduleId) {
    jdbc.update("UPDATE subscription_schedules SET status='SKIPPED' WHERE id=?", scheduleId);
  }

  long insertScheduledAndReturnId(long subscriptionId, LocalDate date) {
    insertScheduled(subscriptionId, date);
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  void retargetPendingPlanChange(long subscriptionId, long scheduleId) {
    jdbc.update(
        "UPDATE pending_plan_changes SET target_schedule_id=? WHERE subscription_id=?",
        scheduleId,
        subscriptionId);
  }

  void setSubscriptionStatus(long subscriptionId, String status) {
    jdbc.update("UPDATE subscriptions SET status=? WHERE id=?", status, subscriptionId);
  }

  void setScheduleStatus(long scheduleId, String status) {
    jdbc.update(
        "UPDATE subscription_schedules SET status=?,hold_reason=CASE WHEN ?='HELD' THEN hold_reason"
            + " ELSE NULL END WHERE id=?",
        status,
        status,
        scheduleId);
  }

  int scheduleAddonCount(long scheduleId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM subscription_schedule_addons WHERE schedule_id=?",
        Integer.class,
        scheduleId);
  }

  boolean hasScheduleAddon(long scheduleId, long skuId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedule_addons WHERE schedule_id=? AND sku_id=?",
            Integer.class,
            scheduleId,
            skuId)
        > 0;
  }

  void upsertScheduleAddon(long scheduleId, long skuId, int quantity, java.math.BigDecimal price) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_schedule_addons(schedule_id,sku_id,quantity,unit_price_krw,created_at,updated_at)"
            + " VALUES (?,?,?, ?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE"
            + " quantity=VALUES(quantity),unit_price_krw=VALUES(unit_price_krw),updated_at=UTC_TIMESTAMP(6)",
        scheduleId,
        skuId,
        quantity,
        price);
  }

  void deleteScheduleAddon(long scheduleId, long skuId) {
    if (jdbc.update(
            "DELETE FROM subscription_schedule_addons WHERE schedule_id=? AND sku_id=?",
            scheduleId,
            skuId)
        != 1) throw new SubscriptionApiException(404, "ADDON_NOT_FOUND", "Add-on을 찾을 수 없습니다.");
  }

  void deleteScheduleAddons(long subscriptionId) {
    jdbc.update(
        "DELETE addon FROM subscription_schedule_addons addon JOIN subscription_schedules schedule"
            + " ON schedule.id=addon.schedule_id WHERE schedule.subscription_id=? AND"
            + " schedule.status IN ('SCHEDULED','HELD')",
        subscriptionId);
  }

  void moveScheduleAddons(long fromScheduleId, long toScheduleId) {
    jdbc.update(
        "UPDATE subscription_schedule_addons SET schedule_id=? WHERE schedule_id=?",
        toScheduleId,
        fromScheduleId);
  }

  List<ScheduleAddonProjection> findScheduleAddons(long scheduleId) {
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

  List<Long> heldScheduleIds(long subscriptionId) {
    return jdbc.queryForList(
        "SELECT id FROM subscription_schedules WHERE subscription_id=? AND status='HELD' ORDER BY"
            + " scheduled_date,id",
        Long.class);
  }

  boolean scheduleDateTaken(long subscriptionId, LocalDate date, long excludedScheduleId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                + " scheduled_date=? AND id<>?",
            Integer.class,
            subscriptionId,
            date,
            excludedScheduleId)
        > 0;
  }

  void reschedule(long scheduleId, LocalDate date) {
    jdbc.update("UPDATE subscription_schedules SET scheduled_date=? WHERE id=?", date, scheduleId);
  }

  void rescheduleHeld(long scheduleId, LocalDate date) {
    jdbc.update(
        "UPDATE subscription_schedules SET scheduled_date=?,status='SCHEDULED',hold_reason=NULL"
            + " WHERE id=?",
        date,
        scheduleId);
  }

  void cancelUnorderedSchedules(long subscriptionId) {
    jdbc.update(
        "UPDATE subscription_schedules schedule LEFT JOIN subscription_orders existing_order ON"
            + " existing_order.schedule_id=schedule.id SET schedule.status='CANCELED' WHERE"
            + " schedule.subscription_id=? AND schedule.status IN ('SCHEDULED','HELD') AND"
            + " existing_order.id IS NULL",
        subscriptionId);
  }

  void deletePendingPlanChange(long subscriptionId) {
    jdbc.update("DELETE FROM pending_plan_changes WHERE subscription_id=?", subscriptionId);
  }

  boolean incrementVersion(long subscriptionId, long expected) {
    return jdbc.update(
            "UPDATE subscriptions SET version=version+1 WHERE id=? AND version=?",
            subscriptionId,
            expected)
        == 1;
  }

  void insertCommandHistory(long subscriptionId, String command, long before, long after) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_command_history(subscription_id,command_type,occurred_at,version_before,version_after)"
            + " VALUES (?,?,UTC_TIMESTAMP(6),?,?)",
        subscriptionId,
        command,
        before,
        after);
  }

  void deleteDeliveryReminder(long scheduleId) {
    jdbc.update(
        "DELETE FROM notifications WHERE type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " reference_type='SCHEDULE' AND reference_id=?",
        scheduleId);
  }

  void deleteDeliveryReminders(long subscriptionId) {
    jdbc.update(
        "DELETE notification FROM notifications notification JOIN subscription_schedules schedule"
            + " ON schedule.id=notification.reference_id AND notification.reference_type='SCHEDULE'"
            + " WHERE notification.type='SUBSCRIPTION_DELIVERY_REMINDER' AND"
            + " schedule.subscription_id=?",
        subscriptionId);
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

  private StoredIdempotencyResult storedIdempotency(Map<String, Object> row) {
    return new StoredIdempotencyResult(
        (String) row.get("payload_fingerprint"),
        intValue(row, "response_status"),
        (String) row.get("response_body"),
        (String) row.get("location_header"),
        (String) row.get("etag_header"));
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

  PageProjection<SubscriptionProjection> findSubscriptions(
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

  Map<Long, PetProjection> findOwnedPets(long memberId, List<Long> ids) {
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

  Map<Long, SubscriptionSnapshotBase> findSnapshots(List<Long> ids) {
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

  Map<Long, List<SubscriptionItemProjection>> findSnapshotItems(List<Long> snapshotIds) {
    return groupedItems(
        "SELECT snapshot_id,sku_id,quantity FROM subscription_snapshot_items WHERE snapshot_id IN ",
        snapshotIds,
        "snapshot_id");
  }

  Map<Long, LocalDate> findNextSchedules(List<Long> subscriptionIds, LocalDate today) {
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
        (NativeQueryExecutor.RowCallbackHandler)
            rs ->
                result.putIfAbsent(
                    rs.getLong("subscription_id"), rs.getDate("scheduled_date").toLocalDate()),
        withLast(subscriptionIds, today));
    return result;
  }

  Optional<LocalDate> findNextSchedule(long subscriptionId, LocalDate today) {
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

  Optional<Long> findPendingSnapshotId(long subscriptionId) {
    return one(
            "SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?", subscriptionId)
        .map(row -> longValue(row, "snapshot_id"));
  }

  Optional<NextDeliveryProjection> findNextDeliverySchedule(long subscriptionId) {
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

  List<SubscriptionItemDetailProjection> findSnapshotItemDetails(long snapshotId) {
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

  PageProjection<ScheduleViewProjection> findScheduleViews(
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

  PageProjection<CommandHistoryProjection> findCommandHistory(
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

  List<Long> activeSubscriptionIds() {
    return jdbc.queryForList(
        "SELECT id FROM subscriptions WHERE runtime_managed=true AND status='ACTIVE' ORDER BY id",
        Long.class);
  }

  Optional<SubscriptionProjection> lockActiveSubscription(long subscriptionId) {
    return one(
            "SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id"
                + " FROM subscriptions WHERE id=? AND runtime_managed=true AND status='ACTIVE' FOR"
                + " UPDATE",
            subscriptionId)
        .map(this::subscription);
  }

  boolean hasUnprocessedDueSchedule(long subscriptionId, LocalDate today) {
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

  List<ScheduleProjection> futureSchedulesForUpdate(long subscriptionId, LocalDate today) {
    return jdbc.query(
        "SELECT id,scheduled_date FROM subscription_schedules WHERE subscription_id=? AND"
            + " status='SCHEDULED' AND scheduled_date>? ORDER BY scheduled_date,id FOR UPDATE",
        (rs, rowNum) ->
            new ScheduleProjection(
                rs.getLong("id"), rs.getDate("scheduled_date").toLocalDate()),
        subscriptionId,
        today);
  }

  Optional<ProcessedScheduleProjection> lastProcessedSchedule(long subscriptionId) {
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

  boolean scheduleExists(long subscriptionId, LocalDate date) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND"
                + " scheduled_date=?",
            Integer.class,
            subscriptionId,
            date)
        > 0;
  }

  private long snapshot(long subscriptionId, long versionId, int cycle, long price) {
    jdbc.update(
        "INSERT INTO"
            + " subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks)"
            + " VALUES (?,?,?,?)",
        subscriptionId,
        versionId,
        price,
        cycle);
    long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) SELECT"
            + " ?,sku_id,quantity FROM plan_items WHERE plan_version_id=?",
        id,
        versionId);
    return id;
  }

  private long firstSku(long versionId) {
    return jdbc.queryForObject(
        "SELECT sku_id FROM plan_items WHERE plan_version_id=? ORDER BY sku_id LIMIT 1",
        Long.class,
        versionId);
  }

  private Map<Long, List<SubscriptionItemProjection>> groupedItems(
      String prefix, List<Long> ids, String key) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, List<SubscriptionItemProjection>> result = new HashMap<>();
    jdbc.query(
        prefix + placeholders(ids.size()) + " ORDER BY " + key + ",sku_id",
        (NativeQueryExecutor.RowCallbackHandler)
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
        (NativeQueryExecutor.RowCallbackHandler)
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
