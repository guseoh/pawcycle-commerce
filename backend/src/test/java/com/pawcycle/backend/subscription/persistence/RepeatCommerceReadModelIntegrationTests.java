package com.pawcycle.backend.subscription.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.pawcycle.backend.support.ReadQueryParity.compare;

import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepeatCommerceReadModelIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired RepeatCommerceQueryRepository queries;
  @Autowired SubscriptionMetricsQueryRepository metrics;
  SecondaryReadFixtures fixtures;
  LegacyRepeatCommerceQueryRepository legacy;
  long member, otherMember, pet, subscription, plan, snapshot;
  Sku sku, duplicate;
  final LocalDate today = LocalDate.of(2026, 9, 18);

  @BeforeEach void setUp() {
    fixtures = new SecondaryReadFixtures(jdbc, entities);
    legacy = new LegacyRepeatCommerceQueryRepository(jdbc);
    member = fixtures.member(); otherMember = fixtures.member();
    var product = fixtures.product(fixtures.category(true), fixtures.brand(true), "DOG", "PUBLIC");
    sku = fixtures.sku(product, SkuStatus.ACTIVE, 10);
    duplicate = fixtures.sku(product, SkuStatus.ACTIVE, 10);
    jdbc.update("INSERT INTO pets(member_id,name,pet_type) VALUES (?,'Fixture','DOG')", member);
    pet = fixtures.lastId();
    jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,pet_id,runtime_managed,status) VALUES (?,?,1,4,'2026-09-01','2026-09-30',?,true,'ACTIVE')", member, sku.getId(), pet);
    subscription = fixtures.lastId();
    plan = plan("Fixture plan", "DOG", true, false, today, today);
    jdbc.update("INSERT INTO subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks) VALUES (?,?,9007199254740991,4)", subscription, plan);
    snapshot = fixtures.lastId();
    jdbc.update("UPDATE subscriptions SET current_snapshot_id=? WHERE id=?", snapshot, subscription);
    for (Sku item : List.of(sku, duplicate)) jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,1)", plan, item.getId());
    for (int cycle : List.of(8, 2, 4)) jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,?)", plan, cycle);
    entities.flush(); entities.clear();
  }

  @Test void purchaseOrderAndProductReadsPreserveMultiplicityDatesOwnershipAndNoRows() {
    long paid = order(member, "ONE_TIME", "PAID", "SUCCEEDED", "2026-09-18 00:00:00.000001");
    fixtures.payment(paid, "NORMAL", "FAILED", 2);
    order(member, "ONE_TIME", "PAID", "SUCCEEDED", "2026-09-17 23:59:59.999999");
    order(otherMember, "ONE_TIME", "PAID", "SUCCEEDED", "2026-09-18 00:00:00.000001");
    order(member, "SUBSCRIPTION", "PAID", "SUCCEEDED", "2026-09-18 00:00:00.000001");
    order(member, "ONE_TIME", "PAYMENT_ACTION_REQUIRED", "FAILED", "2026-09-18 00:00:00.000001");
    order(member, "ONE_TIME", "PAID", "FAILED", "2026-09-18 00:00:00.000001");
    order(member, "ONE_TIME", "PAID", "SUCCEEDED", null);
    assertThat(compare(jdbc, () -> legacy.findOneTimePurchaseDates(member), () -> queries.findOneTimePurchaseDates(member)))
        .extracting(RepeatCommerceQueryRepository.PurchaseRow::purchasedDate).containsExactly(today, today.minusDays(1));
    for (long owner : List.of(member, otherMember, Long.MAX_VALUE)) compare(jdbc,
        () -> legacy.findPaidOneTimeOrder(owner, paid), () -> queries.findPaidOneTimeOrder(owner, paid));
    compare(jdbc, () -> legacy.findPaidOneTimeOrder(member, Long.MAX_VALUE), () -> queries.findPaidOneTimeOrder(member, Long.MAX_VALUE));
    assertThat(compare(jdbc, () -> legacy.findOrderProductIds(paid), () -> queries.findOrderProductIds(paid)))
        .containsExactly(sku.getProduct().getId());
    compare(jdbc, () -> legacy.findOrderProductIds(Long.MAX_VALUE), () -> queries.findOrderProductIds(Long.MAX_VALUE));
    compare(jdbc, () -> legacy.findOneTimePurchaseDates(Long.MAX_VALUE), () -> queries.findOneTimePurchaseDates(Long.MAX_VALUE));
  }

  @Test void subscriptionSnapshotAndScheduleReadsPreserveNullMissingStatusAndPaymentBag() {
    for (long owner : List.of(member, otherMember)) compare(jdbc,
        () -> legacy.findRuntimeSubscription(owner, subscription), () -> queries.findRuntimeSubscription(owner, subscription));
    jdbc.update("UPDATE subscriptions SET current_snapshot_id=NULL,status='PAUSED' WHERE id=?", subscription);
    assertThat(compare(jdbc, () -> legacy.findRuntimeSubscription(member, subscription), () -> queries.findRuntimeSubscription(member, subscription)))
        .get().extracting(RepeatCommerceQueryRepository.SubscriptionRow::currentSnapshotId).isEqualTo(0L);
    jdbc.update("UPDATE subscriptions SET runtime_managed=false WHERE id=?", subscription);
    compare(jdbc, () -> legacy.findRuntimeSubscription(member, subscription), () -> queries.findRuntimeSubscription(member, subscription));
    assertThat(compare(jdbc, () -> legacy.findSourcePlanVersionId(snapshot), () -> queries.findSourcePlanVersionId(snapshot))).isEqualTo(plan);
    assertThatThrownBy(() -> queries.findSourcePlanVersionId(Long.MAX_VALUE)).isInstanceOf(EmptyResultDataAccessException.class);
    assertThatThrownBy(() -> legacy.findSourcePlanVersionId(Long.MAX_VALUE)).isInstanceOf(EmptyResultDataAccessException.class);
    long first = scheduleOrder("2026-09-17", "SUBSCRIPTION", "SUCCEEDED");
    fixtures.payment(first, "NORMAL", "FAILED", 2);
    scheduleOrder("2026-09-17", "SUBSCRIPTION", "SUCCEEDED");
    scheduleOrder("2026-09-18", "SUBSCRIPTION", "SUCCEEDED");
    scheduleOrder("2026-09-19", "ONE_TIME", "SUCCEEDED");
    scheduleOrder("2026-09-20", "SUBSCRIPTION", "FAILED");
    assertThat(compare(jdbc, () -> legacy.findSuccessfulScheduleDates(subscription), () -> queries.findSuccessfulScheduleDates(subscription)))
        .containsExactly(today, today.minusDays(1), today.minusDays(1));
    compare(jdbc, () -> legacy.findSuccessfulScheduleDates(Long.MAX_VALUE), () -> queries.findSuccessfulScheduleDates(Long.MAX_VALUE));
  }

  @Test void saleWindowsMigrationNullNamePriceCyclesPetOwnershipAndQueryCostMatchJdbc() {
    plan("Future", "DOG", true, false, today.plusDays(1), null);
    plan("Expired", "DOG", true, false, null, today.minusDays(1));
    plan(null, "DOG", true, false, null, null);
    plan("Hidden", "DOG", false, false, null, null);
    plan("Migration", "DOG", true, true, null, null);
    plan("Cat", "CAT", true, false, null, null);
    plan("Open", "DOG", true, false, null, null);
    jdbc.update("INSERT INTO pets(member_id,name,pet_type) VALUES (?,'Other','DOG'),(?,'Cat','CAT'),(?,'Second','DOG')", otherMember, member, member);
    compare(jdbc, () -> legacy.findAvailablePlanOptions(today, member), () -> queries.findAvailablePlanOptions(today, member));
    compare(jdbc, () -> legacy.findAvailablePlanOptions(today, Long.MAX_VALUE), () -> queries.findAvailablePlanOptions(today, Long.MAX_VALUE));
    assertThat(compare(jdbc, () -> legacy.findAllowedDeliveryCycles(plan), () -> queries.findAllowedDeliveryCycles(plan))).containsExactly(2,4,8);
    compare(jdbc, () -> legacy.findAllowedDeliveryCycles(Long.MAX_VALUE), () -> queries.findAllowedDeliveryCycles(Long.MAX_VALUE));
  }

  @Test void metricsCountUtcDatetimeMicrosecondCutoffNullAndZeroWithSameDbWork() {
    LocalDateTime cutoff = LocalDateTime.of(2026, 9, 18, 0, 0, 0, 123456000);
    for (boolean creation : List.of(true, false)) {
      String table = creation ? "subscription_creation_idempotency_results" : "subscription_command_idempotency_results";
      for (int offset : List.of(-1, 0, 1, 2)) {
        LocalDateTime completed = offset == 2 ? null : cutoff.plusNanos(offset * 1000L);
        String columns = creation ? "" : "subscription_id,command_type,";
        String values = creation ? "" : subscription + ",'PAUSE',";
        jdbc.update("INSERT INTO " + table + "(member_id," + columns + "idempotency_key,payload_fingerprint,completed_at) VALUES (?," + values + "?,REPEAT('a',64),?)", member, "metrics-" + offset, completed);
      }
      compare(jdbc, () -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE completed_at IS NOT NULL", Long.class), () -> metrics.countCompletedRows(creation));
      compare(jdbc, () -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE completed_at < ?", Long.class, cutoff), () -> metrics.countCleanupCandidates(creation, cutoff));
      compare(jdbc, () -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE completed_at < ?", Long.class, (Object) null), () -> metrics.countCleanupCandidates(creation, null));
      compare(jdbc, () -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE completed_at < ?", Long.class, LocalDateTime.of(1900,1,1,0,0)), () -> metrics.countCleanupCandidates(creation, LocalDateTime.of(1900,1,1,0,0)));
    }
  }

  long order(long owner, String source, String status, String payment, String paidAt) {
    long id = fixtures.order(owner, source, status, new BigDecimal("123.45"), paidAt == null ? null : Timestamp.valueOf(paidAt));
    fixtures.payment(id, "NORMAL", payment, 1);
    for (Sku item : List.of(sku, duplicate)) fixtures.item(id, item);
    return id;
  }

  int scheduleDay = 1;
  long scheduleOrder(String date, String source, String payment) {
    jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status) VALUES (?,?,'SCHEDULED')", subscription, LocalDate.of(2026,10,scheduleDay++));
    long schedule = fixtures.lastId();
    long order = order(member, source, "PAID", payment, "2026-09-18 12:00:00");
    jdbc.update("INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,scheduled_date) VALUES (?,?,?,?)", order, subscription, schedule, date);
    return order;
  }

  long plan(String name, String type, boolean sale, boolean migration, LocalDate start, LocalDate end) {
    jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale,sale_starts_on,sale_ends_on) VALUES (?,?,?,?,?)", name, type, sale, start, end);
    long id = fixtures.lastId();
    jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,9007199254740991,?)", id, migration);
    long version = fixtures.lastId();
    jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", version, id);
    return version;
  }
}
