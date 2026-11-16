package com.pawcycle.backend.commerce.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.metrics.persistence.CommerceMetricsQueryRepository;
import com.pawcycle.backend.commerce.metrics.persistence.CommerceMetricsQueryRepository.Backlog;
import com.pawcycle.backend.subscription.persistence.SubscriptionMetricsQueryRepository;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the stage 2 backlog queries against the real schema. Other tests may leave rows behind, so
 * each case checks the change caused by its own fixture rows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BacklogMetricsQueryIntegrationTests {
  private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-18T13:00:00");
  private static final LocalDate TODAY = LocalDate.parse("2026-09-18");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private CommerceMetricsQueryRepository commerce;
  @Autowired private SubscriptionMetricsQueryRepository subscriptions;
  private SecondaryReadFixtures fixtures;
  private long member;
  private long sku;

  @BeforeEach
  void setUp() {
    fixtures = new SecondaryReadFixtures(jdbc, entities);
    member = fixtures.member();
    var product = fixtures.product(fixtures.category(true), fixtures.brand(true), "DOG", "PUBLIC");
    sku = fixtures.sku(product, SkuStatus.ACTIVE, 10).getId();
  }

  @Test
  void processingNormalPaymentCountsOnlyAfterTheGraceCutoff() {
    LocalDateTime cutoff = NOW.minusMinutes(10);
    Supplier<Backlog> query = () -> commerce.normalProcessingExpiredBy(cutoff);
    long before = query.get().count();

    payment("NORMAL", "PROCESSING", "2026-09-18 12:40:00");
    payment("NORMAL", "PROCESSING", "2026-09-18 12:55:00"); // inside the grace window
    payment("BILLING", "PROCESSING", "2026-09-18 12:40:00"); // other kind
    payment("NORMAL", "READY", "2026-09-18 12:40:00"); // not processing

    Backlog after = query.get();
    assertThat(after.count() - before).isEqualTo(1);
    assertThat(after.oldest()).isBeforeOrEqualTo(LocalDateTime.parse("2026-09-18T12:40:00"));
  }

  @Test
  void unknownAndBillingProcessingAreCountedByStatus() {
    long unknown = commerce.unknownPayments().count();
    long billing = commerce.billingProcessing().count();

    payment("NORMAL", "UNKNOWN", null);
    payment("BILLING", "UNKNOWN", null);
    payment("BILLING", "PROCESSING", null);
    payment("BILLING", "SUCCEEDED", null);

    assertThat(commerce.unknownPayments().count() - unknown).isEqualTo(2);
    Backlog afterBilling = commerce.billingProcessing();
    assertThat(afterBilling.count() - billing).isEqualTo(1);
    assertThat(afterBilling.oldest()).isNotNull();
  }

  @Test
  void readyPaymentPastExpiryIsExpirationBacklog() {
    LocalDateTime cutoff = NOW.minusMinutes(1);
    long before = commerce.readyExpiredBy(cutoff).count();

    payment("NORMAL", "READY", "2026-09-18 12:50:00");
    payment("NORMAL", "READY", "2026-09-18 12:59:30"); // inside the grace window
    payment("NORMAL", "READY", null); // no expiry recorded

    assertThat(commerce.readyExpiredBy(cutoff).count() - before).isEqualTo(1);
  }

  @Test
  void unfinishedRefundsAreGroupedByStatus() {
    Map<String, Long> before = commerce.unfinishedRefundsByStatus();
    long order = order();
    long cancellation = fixtures.cancellation(order);
    fixtures.refund(order, cancellation, "FAILED", 1);
    fixtures.refund(order, cancellation, "UNKNOWN", 2);
    fixtures.refund(order, cancellation, "SUCCEEDED", 3);

    Map<String, Long> after = commerce.unfinishedRefundsByStatus();
    assertThat(delta(after, before, "FAILED")).isEqualTo(1);
    assertThat(delta(after, before, "UNKNOWN")).isEqualTo(1);
    assertThat(after).doesNotContainKey("SUCCEEDED");
  }

  @Test
  void overdueCountsOnlyPastScheduledRoundsOfActiveManagedSubscriptions() {
    long before = subscriptions.countOverdueSchedules(TODAY);

    long managed = subscription("ACTIVE", true);
    schedule(managed, "2026-09-10", "SCHEDULED", null); // overdue
    schedule(managed, "2026-09-18", "SCHEDULED", null); // due today, not overdue yet
    schedule(managed, "2026-09-30", "SCHEDULED", null); // future
    schedule(managed, "2026-09-11", "SKIPPED", null);
    schedule(subscription("PAUSED", true), "2026-09-10", "SCHEDULED", null);
    schedule(subscription("ACTIVE", false), "2026-09-10", "SCHEDULED", null);

    assertThat(subscriptions.countOverdueSchedules(TODAY) - before).isEqualTo(1);
  }

  @Test
  void heldRoundsAreGroupedByReason() {
    Map<String, Long> before = subscriptions.countHeldSchedulesByReason();
    long managed = subscription("ACTIVE", true);
    schedule(managed, "2026-09-10", "HELD", "MISSING_BILLING_METHOD");
    schedule(managed, "2026-09-11", "HELD", "MISSING_BILLING_METHOD");
    schedule(managed, "2026-09-12", "HELD", "ORDER_STOCK_UNAVAILABLE");
    schedule(subscription("CANCELED", true), "2026-09-10", "HELD", "MISSING_BILLING_METHOD");

    Map<String, Long> after = subscriptions.countHeldSchedulesByReason();
    assertThat(delta(after, before, "MISSING_BILLING_METHOD")).isEqualTo(2);
    assertThat(delta(after, before, "ORDER_STOCK_UNAVAILABLE")).isEqualTo(1);
  }

  private long order() {
    return fixtures.order(member, "ONE_TIME", "PAYMENT_PENDING", new BigDecimal("123.45"), null);
  }

  private void payment(String type, String status, String expiresAt) {
    long payment = fixtures.payment(order(), type, status, 1);
    // Written through Hibernate, like the queries read it (hibernate.jdbc.time_zone=UTC), so the
    // test does not depend on the JVM time zone the way a raw JDBC string would.
    entities.createQuery("update PaymentEntity p set p.expiresAt = :expiresAt where p.id = :id")
        .setParameter("expiresAt", expiresAt == null ? null : LocalDateTime.parse(expiresAt.replace(' ', 'T')))
        .setParameter("id", payment)
        .executeUpdate();
  }

  private long subscription(String status, boolean managed) {
    jdbc.update(
        "INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date)"
            + " VALUES (?,?,1,4,'2026-09-01','2026-09-30')",
        member, sku);
    long id = fixtures.lastId();
    jdbc.update("UPDATE subscriptions SET status=?, runtime_managed=? WHERE id=?", status, managed, id);
    return id;
  }

  private void schedule(long subscription, String date, String status, String reason) {
    jdbc.update(
        "INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,hold_reason) VALUES (?,?,?,?)",
        subscription, date, status, reason);
  }

  private static long delta(Map<String, Long> after, Map<String, Long> before, String key) {
    return after.getOrDefault(key, 0L) - before.getOrDefault(key, 0L);
  }
}
