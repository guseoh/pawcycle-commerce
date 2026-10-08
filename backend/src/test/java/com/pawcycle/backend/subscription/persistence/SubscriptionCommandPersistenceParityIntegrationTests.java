package com.pawcycle.backend.subscription.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import com.pawcycle.backend.subscription.application.SubscriptionOperationResult;
import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Savepoint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Same transaction/fixture/savepoint, exact main 852e56d JDBC SQL versus the new JPA boundary. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SubscriptionCommandPersistenceParityIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired DataSource dataSource;
  @Autowired SubscriptionAggregatePersistence aggregate;
  @Autowired SubscriptionReadQueries reads;
  @Autowired SubscriptionOrderPersistence orders;
  @Autowired SubscriptionIdempotencyReservationPersistence reservations;
  @Autowired MemberRepository members;
  @Autowired CategoryRepository categories;
  @Autowired ProductRepository products;
  @Autowired SkuRepository skus;
  private long memberId, petId, skuId, addonSkuId, versionId, subscriptionId, snapshotId, scheduleId, pendingId;
  private final LocalDate date = LocalDate.of(2026, 10, 7);
  private final LocalDateTime time = LocalDateTime.parse("2026-10-07T23:59:59.123456");

  @BeforeEach
  void fixture() {
    memberId = members.saveAndFlush(new Member("t09-" + UUID.randomUUID() + "@example.test", "fixture-only")).getId();
    var category = categories.saveAndFlush(new Category("t09-" + UUID.randomUUID(), "T09", 0, true));
    var product = products.saveAndFlush(new Product(category, "T09 product", "fixture", null, "DOG", null, "PUBLIC"));
    skuId = skus.saveAndFlush(com.pawcycle.backend.support.TestSkuFactory.sku(product, "t09-" + UUID.randomUUID(), new BigDecimal("19900.00"), true, 1)).getId();
    addonSkuId = skus.saveAndFlush(com.pawcycle.backend.support.TestSkuFactory.sku(product, "t09-" + UUID.randomUUID(), new BigDecimal("1234.56"), true, 2)).getId();
    jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,20,0,0),(?,20,0,0)", skuId, addonSkuId);
    jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES ('T09','DOG',true)");
    long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,19900,false)", planId);
    versionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", versionId, planId);
    jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,2)", versionId, skuId);
    jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,2)", versionId);
    petId = aggregate.insertPet(memberId, "fixture", "DOG");
    subscriptionId = aggregate.insertSubscription(memberId, versionId, 2, petId, date.minusDays(14), date);
    snapshotId = aggregate.createSnapshot(subscriptionId, versionId, 2, 19900);
    aggregate.setCurrentSnapshot(subscriptionId, snapshotId);
    scheduleId = aggregate.insertScheduledAndReturnId(subscriptionId, date);
    pendingId = aggregate.createSnapshot(subscriptionId, versionId, 4, 9007199254740991L);
    aggregate.replacePendingPlanChange(subscriptionId, pendingId, scheduleId);
    aggregate.upsertScheduleAddon(scheduleId, addonSkuId, 3, new BigDecimal("1234.56"));
  }

  @Test
  void commandAndAutomationReadsAndLocksMatchIncludingMissingRowsAndIndependentDueDates() {
    var old = new LegacySubscriptionCommandQueryReference(jdbc, reads);
    assertThat(aggregate.deliveryCycleAllowed(versionId, 2)).isEqualTo(old.deliveryCycleAllowed(versionId, 2));
    assertThat(aggregate.deliveryCycleAllowed(versionId, 3)).isEqualTo(old.deliveryCycleAllowed(versionId, 3));
    assertThat(aggregate.planContainsSku(versionId, skuId)).isEqualTo(old.planContainsSku(versionId, skuId));
    assertThat(aggregate.planContainsSku(versionId, addonSkuId)).isEqualTo(old.planContainsSku(versionId, addonSkuId));
    assertThat(aggregate.scheduleAddonConflicts(scheduleId, versionId)).isEqualTo(old.scheduleAddonConflicts(scheduleId, versionId));
    assertThat(aggregate.findEligibleAddonSku(addonSkuId)).isEqualTo(old.findEligibleAddonSku(addonSkuId));
    jdbc.update("DELETE FROM inventories WHERE sku_id=?", addonSkuId);
    assertThat(aggregate.findEligibleAddonSku(addonSkuId)).isEqualTo(old.findEligibleAddonSku(addonSkuId));
    assertThat(aggregate.lockOwnedSubscription(memberId, subscriptionId)).isEqualTo(old.lockOwnedSubscription(memberId, subscriptionId));
    assertThat(aggregate.lockActiveSubscription(subscriptionId)).isEqualTo(old.lockActiveSubscription(subscriptionId));
    assertThat(aggregate.lockActiveSubscription(Long.MAX_VALUE)).isEmpty();
    assertThat(aggregate.lockNextScheduled(subscriptionId)).isEqualTo(old.lockNextScheduled(subscriptionId));
    assertThat(aggregate.hasUnprocessedDueSchedule(subscriptionId, date)).isEqualTo(old.hasUnprocessedDueSchedule(subscriptionId, date));
    assertThat(aggregate.hasUnprocessedDueSchedule(subscriptionId, date.minusDays(1))).isEqualTo(old.hasUnprocessedDueSchedule(subscriptionId, date.minusDays(1)));
    aggregate.insertScheduled(subscriptionId, date.plusDays(14));
    assertThat(aggregate.futureSchedulesForUpdate(subscriptionId, date)).isEqualTo(old.futureSchedulesForUpdate(subscriptionId, date));
    assertThat(aggregate.scheduleDateTaken(subscriptionId, date, scheduleId)).isEqualTo(old.scheduleDateTaken(subscriptionId, date, scheduleId));
    assertThat(aggregate.scheduleExists(subscriptionId, date)).isEqualTo(old.scheduleExists(subscriptionId, date));
    assertThat(aggregate.hasScheduleAddon(scheduleId, addonSkuId)).isEqualTo(old.hasScheduleAddon(scheduleId, addonSkuId));
    assertThat(aggregate.findPendingSnapshotId(subscriptionId)).isEqualTo(old.findPendingSnapshotId(subscriptionId));
    assertThat(aggregate.activeSubscriptionIds()).isEqualTo(old.activeSubscriptionIds());
    var orderOld = new LegacySubscriptionOrderReference(jdbc, entities);
    assertThat(orders.lockSubscription(subscriptionId)).isEqualTo(orderOld.lockSubscription(subscriptionId));
    assertThat(orders.lockSchedule(scheduleId)).isEqualTo(orderOld.lockSchedule(scheduleId));
    assertThat(orders.lockSchedule(Long.MAX_VALUE)).isEmpty();
    assertThat(orders.lockExistingOrders(scheduleId)).isEqualTo(orderOld.lockExistingOrders(scheduleId));
    assertThat(orders.findSnapshot(snapshotId, subscriptionId)).isEqualTo(orderOld.findSnapshot(snapshotId, subscriptionId));
    assertThat(orders.findSnapshot(pendingId, subscriptionId)).isEqualTo(orderOld.findSnapshot(pendingId, subscriptionId));
    assertThat(orders.findSnapshot(snapshotId, Long.MAX_VALUE)).isEmpty();
    assertThat(orders.findSnapshotItems(snapshotId)).isEqualTo(orderOld.findSnapshotItems(snapshotId));
    assertThat(orders.findPricedSnapshotItems(snapshotId)).isEqualTo(orderOld.findPricedSnapshotItems(snapshotId));
    assertThat(orders.lockAddOns(scheduleId)).isEqualTo(orderOld.lockAddOns(scheduleId));
    assertThat(orders.lockPendingChange(subscriptionId)).isEqualTo(orderOld.lockPendingChange(subscriptionId));
    assertThat(orders.lockFutureSchedules(subscriptionId, date)).isEqualTo(orderOld.lockFutureSchedules(subscriptionId, date));
    assertThat(orders.lockBillingMethod(memberId)).isEqualTo(orderOld.lockBillingMethod(memberId));
    assertThat(orders.lockDefaultAddress(memberId)).isEqualTo(orderOld.lockDefaultAddress(memberId));
    assertThat(orders.lockShippingSnapshot(subscriptionId)).isEqualTo(orderOld.lockShippingSnapshot(subscriptionId));
    assertThat(orders.findInventory(skuId)).isEqualTo(orderOld.findInventory(skuId));
    assertThat(orders.lockAvailableQuantity(skuId)).isEqualTo(orderOld.lockAvailableQuantity(skuId));
    for (LocalDate cutoff : List.of(date.minusDays(1), date, date.plusDays(20))) {
      assertThat(orders.findDueCandidates(cutoff, date, 1)).isEqualTo(orderOld.findDueCandidates(cutoff, date, 1));
    }
    assertThat(aggregate.lastProcessedSchedule(subscriptionId)).isEqualTo(old.lastProcessedSchedule(subscriptionId));
  }

  @Test
  void fullOrderDatabaseRecordAndConditionalWriteDigestMatchesFrozenSql() throws Exception {
    Connection connection = DataSourceUtils.getConnection(dataSource);
    Savepoint before = connection.setSavepoint();
    var expected = orderDigest(new LegacySubscriptionOrderReference(jdbc, entities));
    connection.rollback(before);
    assertThat(orderDigest(orders)).isEqualTo(expected);
    var old = new LegacySubscriptionCommandQueryReference(jdbc, reads);
    assertThat(aggregate.lastProcessedSchedule(subscriptionId)).isEqualTo(old.lastProcessedSchedule(subscriptionId));
    assertThat(orders.lockExistingOrders(scheduleId)).hasSize(1);
  }

  private List<Object> orderDigest(Object store) throws Exception {
    var result = new ArrayList<Object>();
    result.add(call(store, "insertShippingSnapshot", subscriptionId, "recipient", "fixture-phone", "fixture", "line1", null, time));
    result.add(call(store, "insertOrder", "t09-" + UUID.randomUUID(), memberId, new BigDecimal("23603.68"), new BigDecimal("23603.68"), "recipient", "fixture-phone", "fixture", "line1", null, time));
    long orderId = (Long) call(store, "lastInsertedId");
    result.add(call(store, "insertOrderContext", orderId, subscriptionId, scheduleId, snapshotId, versionId, date));
    result.add(call(store, "insertBillingPayment", orderId, new BigDecimal("23603.68"), "t09-" + UUID.randomUUID(), "t09-" + UUID.randomUUID(), 3, time.minusMinutes(1), time));
    long paymentId = (Long) call(store, "lastInsertedId");
    result.add(call(store, "reserveInventory", 2, 3, skuId, 0L, 5));
    result.add(call(store, "reserveInventory", 1, 1, skuId, 0L, 1)); // stale version
    result.add(call(store, "reserveInventory", 1, 1, skuId, 1L, 100)); // insufficient minimum
    result.add(call(store, "reserveInventory", 1, 1, Long.MAX_VALUE, 0L, 1)); // missing row
    result.add(call(store, "insertReservationMovement", skuId, paymentId, 2, 20L, 18L, 0L, 3L, time));
    result.add(call(store, "insertOrderItem", orderId, skuId, "code", "product", "sku", new BigDecimal("19900.00"), 2, new BigDecimal("39800.00")));
    result.add(call(store, "insertSubscriptionOrder", memberId, subscriptionId, scheduleId, snapshotId, versionId, date, time, new BigDecimal("23603.68")));
    long subscriptionOrder = (Long) call(store, "lastInsertedId");
    result.add(call(store, "insertSubscriptionOrderItem", subscriptionOrder, skuId, 2));
    result.add(call(store, "insertSubscriptionOrderAddOn", subscriptionOrder, addonSkuId, 3, new BigDecimal("1234.56")));
    result.add(call(store, "setEffectiveSnapshot", snapshotId, scheduleId));
    result.add(call(store, "promoteSnapshot", pendingId, 4, subscriptionId, snapshotId, 2));
    result.add(call(store, "promoteSnapshot", snapshotId, 2, subscriptionId, snapshotId, 2)); // stale snapshot
    result.add(call(store, "deletePendingChange", subscriptionId, pendingId, scheduleId));
    result.add(call(store, "deletePendingChange", subscriptionId, pendingId, scheduleId));
    result.add(call(store, "deleteScheduleAddOns", scheduleId));
    result.add(call(store, "deleteReminder", scheduleId));
    result.add(call(store, "insertFutureSchedule", subscriptionId, date.plusDays(28)));
    result.add(call(store, "incrementVersion", subscriptionId, 0L));
    result.add(call(store, "incrementVersion", subscriptionId, 0L));
    result.add(jdbc.queryForMap("SELECT source,status,original_amount,discount_amount,shipping_fee,payment_amount,recipient_name,recipient_phone,postal_code,address_line1,address_line2,CAST(created_at AS CHAR) created_at FROM orders WHERE id=?", orderId));
    result.add(jdbc.queryForMap("SELECT type,provider,status,amount,attempt_no,CAST(requested_at AS CHAR) requested_at,CAST(created_at AS CHAR) created_at,expires_at,reconciliation_attempts FROM payments WHERE id=? AND order_id=?", paymentId, orderId));
    result.add(jdbc.queryForMap("SELECT type,quantity,available_before,available_after,reserved_before,reserved_after,source_id,cancellation_id,return_id,CAST(created_at AS CHAR) created_at FROM inventory_movements WHERE payment_id=?", paymentId));
    result.add(jdbc.queryForMap("SELECT sku_id,quantity,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,line_amount FROM order_items WHERE order_id=?", orderId));
    result.add(jdbc.queryForMap("SELECT subscription_id,schedule_id,effective_snapshot_id,source_plan_version_id,CAST(scheduled_date AS CHAR) scheduled_date FROM subscription_order_context WHERE order_id=?", orderId));
    result.add(jdbc.queryForMap("SELECT recipient_name,recipient_phone,postal_code,address_line1,address_line2,CAST(updated_at AS CHAR) updated_at FROM subscription_shipping_snapshots WHERE subscription_id=?", subscriptionId));
    result.add(jdbc.queryForMap("SELECT schedule_id,effective_snapshot_id,source_plan_version_id,CAST(scheduled_date AS CHAR) scheduled_date,CAST(processed_at AS CHAR) processed_at,package_total_krw,status FROM subscription_orders WHERE id=?", subscriptionOrder));
    result.add(jdbc.queryForList("SELECT sku_id,quantity FROM subscription_order_items WHERE order_id=? ORDER BY sku_id", subscriptionOrder));
    result.add(jdbc.queryForList("SELECT sku_id,quantity,unit_price_krw FROM subscription_order_addon_items WHERE subscription_order_id=? ORDER BY sku_id", subscriptionOrder));
    result.add(orders.findInventory(skuId));
    result.add(jdbc.queryForMap("SELECT version,current_snapshot_id,delivery_cycle_weeks FROM subscriptions WHERE id=?", subscriptionId));
    result.add(jdbc.queryForList("SELECT CAST(scheduled_date AS CHAR) scheduled_date,status,hold_reason,effective_snapshot_id FROM subscription_schedules WHERE subscription_id=? ORDER BY scheduled_date,id", subscriptionId));
    return result;
  }

  @Test
  void aggregateMutationSnapshotCopyNullableProfileAndVersionCasMatchFrozenSql() throws Exception {
    Connection connection = DataSourceUtils.getConnection(dataSource);
    Savepoint before = connection.setSavepoint();
    var expected = aggregateDigest(new LegacySubscriptionWriteReference(jdbc));
    connection.rollback(before);
    assertThat(aggregateDigest(aggregate)).isEqualTo(expected);
  }

  private List<Object> aggregateDigest(Object store) throws Exception {
    var result = new ArrayList<Object>();
    long pet = (Long) call(store, "insertPet", memberId, "nullable", "DOG");
    call(store, "updatePet", memberId, pet, "renamed", true, "breed", true, new BigDecimal("3.20"), true);
    call(store, "updatePet", memberId, pet, null, false, null, true, null, true);
    result.add(jdbc.queryForMap("SELECT member_id,name,pet_type,breed,weight_kg FROM pets WHERE id=?", pet));
    long sub = (Long) call(store, "insertSubscription", memberId, versionId, 2, petId, date.minusDays(14), date);
    long snapshot = (Long) call(store, "createSnapshot", sub, versionId, 2, 9007199254740991L);
    call(store, "setCurrentSnapshot", sub, snapshot);
    long first = (Long) call(store, "insertScheduledAndReturnId", sub, date);
    call(store, "insertScheduled", sub, date.plusDays(14));
    long second = jdbc.queryForObject("SELECT id FROM subscription_schedules WHERE subscription_id=? AND scheduled_date=?", Long.class, sub, date.plusDays(14));
    call(store, "replacePendingPlanChange", sub, snapshot, first);
    call(store, "replacePendingPlanChange", sub, snapshot, second);
    call(store, "retargetPendingPlanChange", sub, first);
    call(store, "upsertScheduleAddon", first, addonSkuId, 1, new BigDecimal("1234.56"));
    String created = jdbc.queryForObject("SELECT CAST(created_at AS CHAR) FROM subscription_schedule_addons WHERE schedule_id=? AND sku_id=?", String.class, first, addonSkuId);
    call(store, "upsertScheduleAddon", first, addonSkuId, 3, new BigDecimal("9876.54"));
    assertThat(jdbc.queryForObject("SELECT CAST(created_at AS CHAR) FROM subscription_schedule_addons WHERE schedule_id=? AND sku_id=?", String.class, first, addonSkuId)).isEqualTo(created);
    call(store, "moveScheduleAddons", first, second);
    result.add(jdbc.queryForList("SELECT sku_id,quantity,unit_price_krw FROM subscription_schedule_addons WHERE schedule_id=?", second));
    call(store, "deleteScheduleAddon", second, addonSkuId);
    call(store, "upsertScheduleAddon", first, addonSkuId, 1, new BigDecimal("1234.56"));
    call(store, "setSubscriptionPet", sub, petId);
    call(store, "markSkipped", first);
    call(store, "rescheduleHeld", first, date.plusDays(1));
    jdbc.update("UPDATE subscription_schedules SET hold_reason='ORDER_STOCK_UNAVAILABLE' WHERE id=?", first);
    call(store, "setScheduleStatus", first, "held");
    result.add(jdbc.queryForMap("SELECT status,hold_reason FROM subscription_schedules WHERE id=?", first));
    call(store, "setScheduleStatus", first, "SCHEDULED");
    call(store, "reschedule", first, date.plusDays(2));
    call(store, "setSubscriptionStatus", sub, "PAUSED");
    result.add(call(store, "incrementVersion", sub, 0L));
    result.add(call(store, "incrementVersion", sub, 0L));
    call(store, "insertCommandHistory", sub, "PAUSE", 0L, 1L);
    assertThat(jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,occurred_at,UTC_TIMESTAMP(6)))<=2 FROM subscription_command_history WHERE subscription_id=?", Boolean.class, sub)).isTrue();
    result.add(jdbc.queryForMap("SELECT command_type,version_before,version_after FROM subscription_command_history WHERE subscription_id=?", sub));
    call(store, "deleteDeliveryReminder", first);
    call(store, "deleteDeliveryReminders", sub);
    call(store, "deleteScheduleAddons", sub);
    call(store, "cancelUnorderedSchedules", sub);
    call(store, "deletePendingPlanChange", sub);
    result.add(jdbc.queryForMap("SELECT member_id,sku_id,quantity,delivery_cycle_weeks,CAST(created_date AS CHAR) created_date,CAST(next_order_date AS CHAR) next_order_date,pet_id,status,version,legacy_api_visible,runtime_managed FROM subscriptions WHERE id=?", sub));
    result.add(jdbc.queryForMap("SELECT source_plan_version_id,package_total_krw,delivery_cycle_weeks FROM subscription_snapshots WHERE id=?", snapshot));
    result.add(jdbc.queryForList("SELECT sku_id,quantity FROM subscription_snapshot_items WHERE snapshot_id=? ORDER BY sku_id", snapshot));
    result.add(jdbc.queryForList("SELECT CAST(scheduled_date AS CHAR) scheduled_date,status,hold_reason,effective_snapshot_id FROM subscription_schedules WHERE subscription_id=? ORDER BY scheduled_date,id", sub));
    result.add(jdbc.queryForObject("SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id=?", Integer.class, sub));
    result.add(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedule_addons WHERE schedule_id IN (?,?)", Integer.class, first, second));
    return result;
  }

  @Test
  void reservationReplayNullsCaseScopeFirstCompletionAndBodyRepairMatchFrozenSql() throws Exception {
    var old = new LegacySubscriptionReservationReference(jdbc);
    Connection connection = DataSourceUtils.getConnection(dataSource);
    Savepoint before = connection.setSavepoint();
    var expected = reservationDigest(old);
    connection.rollback(before);
    assertThat(reservationDigest(reservations)).isEqualTo(expected);
    assertThatThrownBy(() -> reservations.reserveCreation(Long.MAX_VALUE, "missing", "a".repeat(64)))
        .isInstanceOf(EmptyResultDataAccessException.class);
  }

  private List<Object> reservationDigest(Object store) throws Exception {
    var result = new ArrayList<Object>();
    result.add(call(store, "reserveCreation", memberId, "Key", "a".repeat(64)));
    result.add(call(store, "reserveCreation", memberId, "Key", "b".repeat(64)));
    result.add(call(store, "reserveCreation", memberId, "key", "a".repeat(64)));
    result.add(call(store, "lockCreationResult", memberId, "Key"));
    // Completion uses the original DB UTC clock once; body repair/re-completion must preserve it.
    var response = new SubscriptionOperationResult(201, null, "/api/subscriptions/fixture", "\"0\"", false);
    call(store, "updateCreationResponse", memberId, "Key", subscriptionId, response, "{\"nullable\":null,\"amount\":1234.56}");
    String completed = jdbc.queryForObject("SELECT CAST(completed_at AS CHAR) FROM subscription_creation_idempotency_results WHERE member_id=? AND idempotency_key='Key'", String.class, memberId);
    call(store, "updateStoredCreationBody", memberId, "Key", "{\"repair\":true}");
    call(store, "updateCreationResponse", memberId, "Key", subscriptionId, response, "{\"repair\":true}");
    assertThat(jdbc.queryForObject("SELECT CAST(completed_at AS CHAR) FROM subscription_creation_idempotency_results WHERE member_id=? AND idempotency_key='Key'", String.class, memberId)).isEqualTo(completed);
    result.add(call(store, "lockCreationResult", memberId, "Key"));
    result.add(call(store, "reserveCommand", memberId, subscriptionId, "PAUSE", "Key", "a".repeat(64)));
    result.add(call(store, "reserveCommand", memberId, subscriptionId, "PAUSE", "Key", "b".repeat(64)));
    result.add(call(store, "reserveCommand", memberId, subscriptionId, "RESUME", "Key", "b".repeat(64)));
    result.add(call(store, "lockCommandResult", memberId, subscriptionId, "PAUSE", "Key"));
    call(store, "updateCommandResponse", memberId, subscriptionId, "PAUSE", "Key", response, "{\"repair\":false}");
    call(store, "updateStoredCommandBody", memberId, subscriptionId, "PAUSE", "Key", "{\"repair\":true}");
    result.add(call(store, "lockCommandResult", memberId, subscriptionId, "PAUSE", "Key"));
    return result;
  }

  private Object call(Object target, String method, Object... arguments) throws Exception {
    var found = java.util.Arrays.stream(target.getClass().getMethods())
        .filter(value -> value.getName().equals(method) && value.getParameterCount() == arguments.length).findFirst().orElseThrow();
    try {
      return found.invoke(target, arguments);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof Exception cause) throw cause;
      throw failure;
    }
  }
}
