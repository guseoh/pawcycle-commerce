package com.pawcycle.backend.subscription.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.pawcycle.backend.support.ReadQueryParity.compare;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.notification.application.NotificationService;
import com.pawcycle.backend.subscription.automation.SubscriptionDeliveryReminderProcessor;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.sql.Savepoint;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
@Transactional
class SubscriptionDeliveryReminderIntegrationTests {
  private static final LocalDate TODAY = LocalDate.of(2050, 1, 2);
  // Jan 1 in UTC, Jan 2 in Seoul: processor must use Seoul regardless of JVM timezone.
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2050-01-01T16:00:00Z"), ZoneOffset.UTC);
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PlatformTransactionManager manager;
  @Autowired SubscriptionDeliveryReminderPersistence adapter;
  @Autowired NotificationService notifications;
  SecondaryReadFixtures f;
  long member, other, sku, version;

  @BeforeEach void fixture() {
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      f = new SecondaryReadFixtures(jdbc, entities);
      member = f.member(); other = f.member();
      var product = f.product(f.category(true), f.brand(true), "DOG", "PUBLIC");
      sku = f.sku(product, SkuStatus.ACTIVE, null).getId();
      jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES ('T12B plan','DOG',true)");
      long plan = f.lastId();
      jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,100,false)", plan);
      version = f.lastId();
    });
  }

  @Test void exactDateWindowStatusManagementAndOrderPredicatesMatchFrozenAndDbWork() {
    schedule(member, "ACTIVE", true, "SCHEDULED", TODAY);
    long tomorrow = schedule(member, "ACTIVE", true, "SCHEDULED", TODAY.plusDays(1));
    long through = schedule(member, "ACTIVE", true, "SCHEDULED", TODAY.plusDays(3));
    schedule(member, "ACTIVE", true, "SCHEDULED", TODAY.plusDays(4));
    schedule(member, "PAUSED", true, "SCHEDULED", TODAY.plusDays(2));
    schedule(member, "CANCELED", true, "SCHEDULED", TODAY.plusDays(2));
    schedule(member, "ACTIVE", false, "SCHEDULED", TODAY.plusDays(2));
    for (String state : List.of("SKIPPED", "HELD", "CANCELED"))
      schedule(member, "ACTIVE", true, state, TODAY.plusDays(2));
    long ordered = schedule(member, "ACTIVE", true, "SCHEDULED", TODAY.plusDays(2));
    ordered(ordered);
    long foreign = schedule(other, "ACTIVE", true, "SCHEDULED", TODAY.plusDays(1));
    var legacy = new LegacySubscriptionDeliveryReminderPersistence(jdbc);
    var targets = compare(jdbc, () -> legacy.findEligible(TODAY, TODAY.plusDays(3)),
        () -> adapter.findEligible(TODAY, TODAY.plusDays(3)));
    assertThat(targets.stream().filter(t -> t.memberId() == member || t.memberId() == other).toList())
        .containsExactlyInAnyOrder(new SubscriptionDeliveryReminderPersistence.ReminderTarget(member, tomorrow),
            new SubscriptionDeliveryReminderPersistence.ReminderTarget(member, through),
            new SubscriptionDeliveryReminderPersistence.ReminderTarget(other, foreign));
    compare(jdbc, () -> legacy.findEligible(TODAY, TODAY), () -> adapter.findEligible(TODAY, TODAY));
  }

  @Test void atomicDeleteMatchesFrozenAffectedRowsAndScopeIncludingReadAndDanglingReminders() {
    long eligible = schedule(member, "ACTIVE", true, "SCHEDULED", TODAY.plusDays(1));
    long paused = schedule(member, "PAUSED", true, "SCHEDULED", TODAY.plusDays(1));
    long expired = schedule(other, "ACTIVE", true, "SCHEDULED", TODAY);
    notifications.create(member, "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", eligible);
    notifications.create(member, "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", paused);
    notifications.create(other, "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", expired);
    notifications.create(member, "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", Long.MAX_VALUE);
    notifications.create(member, "SUBSCRIPTION_DELIVERY_REMINDER", "ORDER", paused);
    notifications.create(member, "ORDER_PAID", "SCHEDULE", paused);
    notifications.readAll(member);
    Savepoint point = jdbc.execute((ConnectionCallback<Savepoint>) connection -> connection.setSavepoint());
    int expected = new LegacySubscriptionDeliveryReminderPersistence(jdbc).deleteIneligible(TODAY, TODAY.plusDays(3));
    var remaining = notificationIds();
    jdbc.execute((ConnectionCallback<Void>) connection -> { connection.rollback(point); connection.releaseSavepoint(point); return null; });
    long before = counter("Com_delete");
    long selects = counter("Com_select");
    assertThat(adapter.deleteIneligible(TODAY, TODAY.plusDays(3))).isEqualTo(expected);
    assertThat(counter("Com_delete")).isEqualTo(before + 1);
    assertThat(counter("Com_select")).isEqualTo(selects);
    assertThat(notificationIds()).isEqualTo(remaining);
    assertThat(notifications.list(member)).hasSize(3); // eligible read reminder and two unrelated kinds
    assertThat(notifications.list(other)).isEmpty();
    assertThat(adapter.deleteIneligible(TODAY, TODAY.plusDays(3))).isZero();
  }

  @Test void seoulProcessorRepeatPauseSkipCancelShiftOrderAndReentryPreserveFirstAndFreshRows() {
    long target = schedule(member, "ACTIVE", true, "SCHEDULED", TODAY.plusDays(1));
    long outside = schedule(member, "ACTIVE", true, "SCHEDULED", TODAY);
    var processor = processor();
    processor.process();
    assertThat(notifications.list(member)).hasSize(1);
    var first = notifications.list(member).getFirst();
    assertThat(first.referenceId()).isEqualTo(target);
    processor.process();
    assertThat(notifications.list(member).getFirst()).isEqualTo(first);
    notifications.read(member, first.notificationId());
    for (String state : List.of("PAUSED", "CANCELED")) {
      subscriptionStatus(target, state);
      processor.process(); assertThat(notifications.list(member)).isEmpty();
      subscriptionStatus(target, "ACTIVE"); processor.process();
      var fresh = notifications.list(member).getFirst();
      assertThat(fresh.notificationId()).isGreaterThan(first.notificationId());
      assertThat(fresh.readAt()).isNull();
      first = fresh;
    }
    for (String state : List.of("SKIPPED", "CANCELED")) {
      jdbc.update("UPDATE subscription_schedules SET status=? WHERE id=?", state, target);
      processor.process(); assertThat(notifications.list(member)).isEmpty();
      jdbc.update("UPDATE subscription_schedules SET status='SCHEDULED' WHERE id=?", target);
      processor.process(); assertThat(notifications.list(member)).hasSize(1);
    }
    jdbc.update("UPDATE subscription_schedules SET scheduled_date=? WHERE id=?", TODAY.plusDays(4), target);
    processor.process(); assertThat(notifications.list(member)).isEmpty();
    jdbc.update("UPDATE subscription_schedules SET scheduled_date=? WHERE id=?", TODAY.plusDays(3), target);
    processor.process(); assertThat(notifications.list(member)).hasSize(1);
    ordered(target); processor.process(); assertThat(notifications.list(member)).isEmpty();
    assertThat(notifications.list(member)).noneMatch(n -> n.referenceId() == outside);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void callerFailureRollsBackStaleDeleteAndReplacementCreationTogether() {
    long stale = new TransactionTemplate(manager).execute(status -> {
      long target = schedule(member, "PAUSED", true, "SCHEDULED", TODAY.plusDays(1));
      notifications.create(member, "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", target);
      schedule(member, "ACTIVE", true, "SCHEDULED", TODAY.plusDays(1));
      notifications.readAll(member);
      return notifications.list(member).getFirst().notificationId();
    });
    var original = notifications.list(member).getFirst();
    assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
      processor().process();
      assertThat(notifications.list(member)).hasSize(1);
      assertThat(notifications.list(member).getFirst().notificationId()).isNotEqualTo(stale);
      throw new IllegalStateException("after reminder cleanup and create");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(notifications.list(member)).containsExactly(original);
  }

  private long schedule(long owner, String status, boolean managed, String state, LocalDate date) {
    jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,status,runtime_managed) VALUES (?,?,1,2,?,?,?,?)", owner, sku, TODAY.minusDays(1), date, status, managed);
    long subscription = f.lastId();
    jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status) VALUES (?,?,?)", subscription, date, state);
    return f.lastId();
  }
  private void ordered(long schedule) {
    long subscription = jdbc.queryForObject("SELECT subscription_id FROM subscription_schedules WHERE id=?", Long.class, schedule);
    jdbc.update("INSERT INTO subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks) VALUES (?,?,100,2)", subscription, version);
    long snapshot = f.lastId();
    jdbc.update("INSERT INTO subscription_orders(member_id,subscription_id,schedule_id,effective_snapshot_id,source_plan_version_id,scheduled_date,processed_at,package_total_krw,status) SELECT member_id,?, ?, ?, ?, ?, '2050-01-02 00:00:00.123456',100,'CREATED' FROM subscriptions WHERE id=?", subscription, schedule, snapshot, version, TODAY, subscription);
  }
  private void subscriptionStatus(long schedule, String state) {
    jdbc.update("UPDATE subscriptions SET status=? WHERE id=(SELECT subscription_id FROM subscription_schedules WHERE id=?)", state, schedule);
  }
  private List<Long> notificationIds() {
    return jdbc.queryForList("SELECT id FROM notifications WHERE member_id IN (?,?) ORDER BY id", Long.class, member, other);
  }
  private long counter(String name) { return jdbc.queryForObject("SHOW SESSION STATUS LIKE ?", (rs, row) -> rs.getLong(2), name); }
  private SubscriptionDeliveryReminderProcessor processor() { return new SubscriptionDeliveryReminderProcessor(adapter, notifications, CLOCK, 3); }
}
