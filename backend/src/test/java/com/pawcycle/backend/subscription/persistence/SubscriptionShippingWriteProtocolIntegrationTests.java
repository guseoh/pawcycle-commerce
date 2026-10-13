package com.pawcycle.backend.subscription.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.member.address.application.MemberAddressApplicationService;
import com.pawcycle.backend.member.address.application.MemberAddressCommand;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.support.AfterSalesFixtures;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionShippingWriteProtocolIntegrationTests {
  private static final Instant NOW = Instant.parse("2026-10-13T01:02:03.123456Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PlatformTransactionManager manager;
  @Autowired MemberAddressApplicationService addresses;
  private final java.util.Set<Long> ownedSubscriptions = new java.util.HashSet<>();
  private final java.util.Set<Long> ownedMembers = new java.util.HashSet<>();

  @AfterEach
  void cleanCommittedFixtures() {
    tx().executeWithoutResult(s -> {
      for (long subscription : ownedSubscriptions) {
        jdbc.update("DELETE FROM subscription_order_context WHERE subscription_id=?", subscription);
        jdbc.update("DELETE FROM subscription_shipping_snapshots WHERE subscription_id=?", subscription);
        jdbc.update("DELETE FROM subscription_schedules WHERE subscription_id=?", subscription);
        jdbc.update("DELETE FROM subscriptions WHERE id=?", subscription);
      }
      for (long member : ownedMembers) {
        jdbc.update("UPDATE members SET default_address_id=NULL WHERE id=?", member);
        jdbc.update("DELETE FROM member_addresses WHERE member_id=?", member);
        jdbc.update("DELETE FROM orders WHERE member_id=?", member);
        jdbc.update("DELETE FROM members WHERE id=?", member);
      }
    });
  }

  @Test
  void ownership404AndNoUnorderedScheduledOrHeld409MatchFrozen() {
    rollback(() -> {
      var f = fixture();
      for (boolean frozen : List.of(false, true)) {
        assertError(frozen, f.member()+1000000, f.subscription(), 404, "SUBSCRIPTION_NOT_FOUND");
        jdbc.update("UPDATE subscription_schedules SET status='CANCELED' WHERE subscription_id=?", f.subscription());
        assertError(frozen, f.member(), f.subscription(), 409, "SUBSCRIPTION_SHIPPING_NOT_CHANGEABLE");
        jdbc.update("UPDATE subscription_schedules SET status='HELD',hold_reason='MISSING_SHIPPING_ADDRESS' WHERE subscription_id=?", f.subscription());
        ordered(f, f.schedule());
        assertError(frozen, f.member(), f.subscription(), 409, "SUBSCRIPTION_SHIPPING_NOT_CHANGEABLE");
        jdbc.update("DELETE FROM subscription_order_context WHERE subscription_id=?", f.subscription());
      }
      assertThat(snapshot(f)).isEmpty();
    });
  }

  @Test
  void singleUpsertAllFieldsNullTimestampAndConditionalHoldReleaseMatchFrozen() {
    rollback(() -> {
      var f = fixture();
      long otherHold = schedule(f, "2026-11-02", "HELD", "MISSING_BILLING_METHOD");
      long ordered = schedule(f, "2026-11-03", "HELD", "MISSING_SHIPPING_ADDRESS"); ordered(f, ordered);
      long scheduled = schedule(f, "2026-11-04", "SCHEDULED", null);
      for (String line2 : new String[]{"suite", null}) {
        reset(f); long selects = counter("Com_select"), inserts = counter("Com_insert"), updates = counter("Com_update_multi");
        update(false, f.member(), f.subscription(), line2);
        assertThat(counter("Com_select") - selects).isEqualTo(2);
        assertThat(counter("Com_insert") - inserts).isEqualTo(1);
        assertThat(counter("Com_update_multi") - updates).isEqualTo(1);
        var expected = snapshot(f); var expectedStates = states(f);
        Timestamp stored = jdbc.queryForObject(
            "SELECT updated_at FROM subscription_shipping_snapshots WHERE subscription_id=?",
            Timestamp.class, f.subscription());
        assertThat(stored).isEqualTo(Timestamp.from(NOW));
        assertThat(stored.getNanos()).isEqualTo(123456000);
        assertThat(expected.getFirst().get("address_line2")).isEqualTo(line2);
        assertThat(jdbc.queryForObject("SELECT hold_reason FROM subscription_schedules WHERE id=?", String.class, otherHold)).isEqualTo("MISSING_BILLING_METHOD");
        assertThat(jdbc.queryForObject("SELECT status FROM subscription_schedules WHERE id=?", String.class, ordered)).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT status FROM subscription_schedules WHERE id=?", String.class, scheduled)).isEqualTo("SCHEDULED");
        reset(f); selects = counter("Com_select"); inserts = counter("Com_insert"); updates = counter("Com_update_multi");
        update(true, f.member(), f.subscription(), line2);
        assertThat(counter("Com_select") - selects).isEqualTo(2);
        assertThat(counter("Com_insert") - inserts).isEqualTo(1);
        assertThat(counter("Com_update_multi") - updates).isEqualTo(1);
        assertThat(snapshot(f)).isEqualTo(expected); assertThat(states(f)).isEqualTo(expectedStates);
      }
    });
  }

  @Test
  void addressReleasePredicatesAndPhysicalFootprintMatchFrozen() {
    rollback(() -> {
      var f = fixture(); long other = new SecondaryReadFixtures(jdbc, entities).member();
      long address = addresses.create(f.member(), address("first"));
      long ordered = schedule(f, "2026-11-02", "HELD", "MISSING_SHIPPING_ADDRESS"); ordered(f, ordered);
      long otherHold = schedule(f, "2026-11-03", "HELD", "MISSING_BILLING_METHOD");
      for (String subscriptionStatus : List.of("ACTIVE", "PAUSED", "CANCELED")) {
        for (boolean hasDefault : List.of(false, true)) {
          for (boolean hasSnapshot : List.of(false, true)) {
            jdbc.update("UPDATE subscriptions SET status=? WHERE id=?", subscriptionStatus, f.subscription());
            jdbc.update("UPDATE members SET default_address_id=? WHERE id=?", hasDefault ? address : null, f.member());
            jdbc.update("DELETE FROM subscription_shipping_snapshots WHERE subscription_id=?", f.subscription());
            if (hasSnapshot) update(false, f.member(), f.subscription(), "suite");
            reset(f);
            actual().releaseAddressHolds(f.member());
            var expected = states(f);
            String target = jdbc.queryForObject("SELECT status FROM subscription_schedules WHERE id=?", String.class, f.schedule());
            assertThat(target).isEqualTo(subscriptionStatus.equals("ACTIVE") && hasDefault && !hasSnapshot ? "SCHEDULED" : "HELD");
            reset(f); legacy().releaseAddressHolds(f.member()); assertThat(states(f)).isEqualTo(expected);
            assertThat(jdbc.queryForObject("SELECT status FROM subscription_schedules WHERE id=?", String.class, ordered)).isEqualTo("HELD");
            assertThat(jdbc.queryForObject("SELECT hold_reason FROM subscription_schedules WHERE id=?", String.class, otherHold)).isEqualTo("MISSING_BILLING_METHOD");
            reset(f); actual().releaseAddressHolds(other);
            assertThat(jdbc.queryForObject("SELECT status FROM subscription_schedules WHERE id=?", String.class, f.schedule())).isEqualTo("HELD");
          }
        }
      }
    });
    var f = tx().execute(s -> fixture());
    assertThat(updateLocks(f, false)).isNotEmpty().isEqualTo(updateLocks(f, true));
    tx().executeWithoutResult(s -> { addresses.create(f.member(), address("physical")); reset(f); });
    assertThat(releaseLocks(f, false)).isNotEmpty().isEqualTo(releaseLocks(f, true));
  }

  @Test
  void defaultAddressFlushDirtyMemberVisibilityAndCallerRollbackArePreserved() {
    var f = tx().execute(s -> fixture());
    assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
      addresses.create(f.member(), address("rollback"));
      update(false, f.member(), f.subscription(), null);
      throw new IllegalStateException("caller rollback");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(snapshot(f)).isEmpty();
    assertThat(jdbc.queryForObject("SELECT status FROM subscription_schedules WHERE id=?", String.class, f.schedule())).isEqualTo("HELD");
    assertThat(jdbc.queryForObject("SELECT default_address_id FROM members WHERE id=?", Long.class, f.member())).isNull();
    tx().executeWithoutResult(s -> addresses.create(f.member(), address("committed")));
    assertThat(jdbc.queryForObject("SELECT status FROM subscription_schedules WHERE id=?", String.class, f.schedule())).isEqualTo("SCHEDULED");
    tx().executeWithoutResult(s -> {
      long second = addresses.create(f.member(), address("second"));
      jdbc.update("UPDATE subscription_schedules SET status='HELD',hold_reason='MISSING_SHIPPING_ADDRESS' WHERE id=?", f.schedule());
      addresses.makeDefault(f.member(), second);
      assertThat(jdbc.queryForObject("SELECT default_address_id FROM members WHERE id=?", Long.class, f.member())).isEqualTo(second);
      assertThat(jdbc.queryForObject("SELECT status FROM subscription_schedules WHERE id=?", String.class, f.schedule())).isEqualTo("SCHEDULED");
      Member dirty = entities.find(Member.class, f.member()); dirty.clearDefaultAddress();
      reset(f); actual().releaseAddressHolds(f.member());
      assertThat(jdbc.queryForObject("SELECT status FROM subscription_schedules WHERE id=?", String.class, f.schedule())).isEqualTo("SCHEDULED");
      update(false, f.member(), f.subscription(), null);
      assertThat(jdbc.queryForObject("SELECT default_address_id FROM members WHERE id=?", Long.class, f.member())).isEqualTo(second);
      s.setRollbackOnly();
    });
  }

  @Test
  void competingAtomicUpsertsKeepOneCompleteSnapshotForBothProtocols() throws Exception {
    for (boolean frozen : List.of(false, true)) {
      var f = tx().execute(s -> fixture());
      var results = AfterSalesFixtures.race(
          () -> tx().execute(s -> { update(frozen, f.member(), f.subscription(), "first"); return true; }),
          () -> tx().execute(s -> { update(frozen, f.member(), f.subscription(), "second"); return true; }));
      assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
      var snapshot = snapshot(f); assertThat(snapshot).hasSize(1);
      assertThat(snapshot.getFirst().get("recipient_name")).isIn("first", "second");
      assertThat(snapshot.getFirst().get("recipient_phone")).isEqualTo(snapshot.getFirst().get("recipient_name"));
      assertThat(snapshot.getFirst().get("postal_code")).isEqualTo(snapshot.getFirst().get("recipient_name"));
      assertThat(snapshot.getFirst().get("address_line1")).isEqualTo(snapshot.getFirst().get("recipient_name"));
      assertThat(snapshot.getFirst().get("address_line2")).isEqualTo(snapshot.getFirst().get("recipient_name"));
    }
  }

  private List<Map<String,Object>> updateLocks(Fixture f, boolean frozen) {
    return tx().execute(s -> { update(frozen, f.member(), f.subscription(), null); var result = locks(); s.setRollbackOnly(); return result; });
  }
  private List<Map<String,Object>> releaseLocks(Fixture f, boolean frozen) {
    return tx().execute(s -> { if (frozen) legacy().releaseAddressHolds(f.member()); else actual().releaseAddressHolds(f.member()); var result = locks(); s.setRollbackOnly(); return result; });
  }
  private List<Map<String,Object>> locks() {
    return jdbc.queryForList("SELECT OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_DATA FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE() AND THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID()) ORDER BY OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_DATA");
  }
  private void assertError(boolean frozen, long member, long subscription, int status, String code) {
    assertThatThrownBy(() -> update(frozen, member, subscription, null)).isInstanceOfSatisfying(CommerceException.class, e -> { assertThat(e.status()).isEqualTo(status); assertThat(e.code()).isEqualTo(code); });
  }
  private void update(boolean frozen, long member, long subscription, String value) {
    String text = value == null ? "null-field" : value;
    if (frozen) legacy().update(member, subscription, new LegacySubscriptionShippingPersistenceAdapter.ShippingAddress(text, text, text, text, value));
    else actual().update(member, subscription, new SubscriptionShippingPersistenceAdapter.ShippingAddress(text, text, text, text, value));
  }
  private Fixture fixture() {
    var f = new SecondaryReadFixtures(jdbc, entities); long member = f.member(); ownedMembers.add(member);
    var product = f.product(f.category(true), f.brand(true), "DOG", "PUBLIC");
    var sku = f.sku(product, SkuStatus.ACTIVE, 10);
    jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,status) VALUES (?,?,1,2,'2026-10-01','2026-11-01','ACTIVE')", member, sku.getId());
    long subscription = f.lastId(); ownedSubscriptions.add(subscription); var result = new Fixture(member, subscription, 0);
    return new Fixture(member, subscription, schedule(result, "2026-11-01", "HELD", "MISSING_SHIPPING_ADDRESS"));
  }
  private long schedule(Fixture f, String date, String status, String reason) {
    jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,hold_reason) VALUES (?,?,?,?)", f.subscription(), date, status, reason);
    return new SecondaryReadFixtures(jdbc, entities).lastId();
  }
  private void ordered(Fixture f, long schedule) {
    long order = new SecondaryReadFixtures(jdbc, entities).order(f.member(), "SUBSCRIPTION", "PAID", BigDecimal.ONE, Timestamp.from(NOW));
    jdbc.update("INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,scheduled_date) SELECT ?,subscription_id,id,scheduled_date FROM subscription_schedules WHERE id=?", order, schedule);
  }
  private void reset(Fixture f) { jdbc.update("UPDATE subscription_schedules SET status='HELD',hold_reason='MISSING_SHIPPING_ADDRESS' WHERE id=?", f.schedule()); }
  private List<Map<String,Object>> snapshot(Fixture f) { return jdbc.queryForList("SELECT recipient_name,recipient_phone,postal_code,address_line1,address_line2,updated_at FROM subscription_shipping_snapshots WHERE subscription_id=?", f.subscription()); }
  private List<Map<String,Object>> states(Fixture f) { return jdbc.queryForList("SELECT id,status,hold_reason FROM subscription_schedules WHERE subscription_id=? ORDER BY id", f.subscription()); }
  private long counter(String name) { return jdbc.queryForObject("SHOW SESSION STATUS LIKE ?", (rs, row) -> rs.getLong(2), name); }
  private TransactionTemplate tx() { return new TransactionTemplate(manager); }
  private void rollback(Runnable call) { tx().executeWithoutResult(s -> { call.run(); s.setRollbackOnly(); }); }
  private SubscriptionShippingPersistenceAdapter actual() { return new SubscriptionShippingPersistenceAdapter(entities, CLOCK); }
  private LegacySubscriptionShippingPersistenceAdapter legacy() { return new LegacySubscriptionShippingPersistenceAdapter(jdbc, CLOCK); }
  private static MemberAddressCommand address(String name) { return new MemberAddressCommand(name, name, "fixture-phone", "fixture-postal", "fixture-address", null); }
  private record Fixture(long member, long subscription, long schedule) {}
}
