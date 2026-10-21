package com.pawcycle.backend.commerce.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.notification.application.NotificationService;
import com.pawcycle.backend.support.AfterSalesFixtures;
import com.pawcycle.backend.support.AfterSalesFixtures.OrderFixture;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeliveryTransactionIntegrationTests {
  @Autowired private DeliveryService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private PlatformTransactionManager manager;
  @MockitoSpyBean private NotificationService notifications;
  @MockitoSpyBean private AdminAuditService audits;
  private OrderFixture order;
  private long delivery;

  @BeforeEach
  void fixture() {
    order = new AfterSalesFixtures(jdbc, entities, manager).paidOrder("SHIPPED", new BigDecimal("123.45"));
    delivery = jdbc.queryForObject("SELECT id FROM deliveries WHERE order_id=?", Long.class, order.order());
    jdbc.update("UPDATE deliveries SET carrier_code='OLD',tracking_number='old-tracking',shipped_at=? WHERE id=?",
        SecondaryReadFixtures.stamp(), delivery);
  }

  @ParameterizedTest
  @EnumSource(Transition.class)
  void successfulTransitionCommitsDeliveryNotificationAndAudit(Transition transition) {
    prepare(transition);
    DeliveryResult response = apply(transition);
    assertThat(response.status()).isEqualTo(transition.status);
    assertThat(response.deliveryId()).isEqualTo(delivery);
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      assertTransition(transition);
      assertSideEffects(transition);
    });
  }

  @ParameterizedTest
  @CsvSource({"SHIP,NOTIFICATION", "SHIP,AUDIT", "COMPLETE,NOTIFICATION", "COMPLETE,AUDIT", "FAIL,AUDIT"})
  void lateFailureRollsBackEveryDeliveryFieldNotificationAndAudit(Transition transition, String failurePoint) {
    prepare(transition);
    Snapshot before = snapshot();
    if ("NOTIFICATION".equals(failurePoint)) {
      doAnswer(invocation -> {
        invocation.callRealMethod();
        entities.flush();
        assertTransition(transition);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE member_id=? AND reference_type='DELIVERY' AND reference_id=?", Integer.class, order.member(), delivery)).isEqualTo(1);
        throw new IllegalStateException("delivery notification failure");
      }).when(notifications).create(order.member(), transition.notification, "DELIVERY", delivery);
    } else {
      doAnswer(invocation -> {
        invocation.callRealMethod();
        entities.flush();
        assertTransition(transition);
        assertSideEffects(transition);
        throw new IllegalStateException("delivery audit failure");
      }).when(audits).append(order.admin(), transition.audit, "DELIVERY", delivery);
    }
    assertThatThrownBy(() -> apply(transition)).isInstanceOf(IllegalStateException.class)
        .hasMessage("delivery " + failurePoint.toLowerCase(java.util.Locale.ROOT) + " failure");
    assertThat(snapshot()).isEqualTo(before);
    // Metrics are outside the DB transaction; no rollback assertion is made for counters/timers.
  }

  private void prepare(Transition transition) {
    if (transition == Transition.SHIP) {
      jdbc.update("UPDATE deliveries SET status='FAILED',failure_reason='previous failure',failed_at=? WHERE id=?",
          SecondaryReadFixtures.stamp(), delivery);
    }
  }

  private DeliveryResult apply(Transition transition) {
    return switch (transition) {
      case SHIP -> service.ship(order.admin(), delivery, "NEW", "new-tracking");
      case COMPLETE -> service.complete(order.admin(), delivery);
      case FAIL -> service.fail(order.admin(), delivery, "recipient unavailable");
    };
  }

  private void assertTransition(Transition transition) {
    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM deliveries WHERE id=?", delivery);
    assertThat(row).containsEntry("status", transition.status).containsEntry("cancelled_at", null);
    assertThat(jdbc.queryForObject("SELECT shipped_at FROM deliveries WHERE id=?", Timestamp.class, delivery)).isNotNull();
    if (transition == Transition.SHIP) {
      assertThat(row).containsEntry("carrier_code", "NEW").containsEntry("tracking_number", "new-tracking")
          .containsEntry("failure_reason", null).containsEntry("failed_at", null).containsEntry("delivered_at", null);
    } else {
      assertThat(row).containsEntry("carrier_code", "OLD").containsEntry("tracking_number", "old-tracking");
      assertThat(jdbc.queryForObject("SELECT shipped_at FROM deliveries WHERE id=?", Timestamp.class, delivery))
          .isEqualTo(SecondaryReadFixtures.stamp());
      if (transition == Transition.COMPLETE) {
        assertThat(jdbc.queryForObject("SELECT delivered_at FROM deliveries WHERE id=?", Timestamp.class, delivery)).isNotNull();
        assertThat(row).containsEntry("failure_reason", null).containsEntry("failed_at", null);
      } else {
        assertThat(jdbc.queryForObject("SELECT failed_at FROM deliveries WHERE id=?", Timestamp.class, delivery)).isNotNull();
        assertThat(row).containsEntry("failure_reason", "recipient unavailable").containsEntry("delivered_at", null);
      }
    }
  }

  private void assertSideEffects(Transition transition) {
    var events = jdbc.queryForList("SELECT type FROM notifications WHERE member_id=? AND reference_type='DELIVERY' AND reference_id=?", String.class, order.member(), delivery);
    if (transition.notification == null) assertThat(events).isEmpty();
    else assertThat(events).containsExactly(transition.notification);
    assertThat(jdbc.queryForList("SELECT action FROM admin_audit_logs WHERE admin_id=? AND target_type='DELIVERY' AND target_id=?", String.class, order.admin(), delivery))
        .containsExactly(transition.audit);
  }

  private Snapshot snapshot() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    return new TransactionTemplate(manager).execute(status -> new Snapshot(
        jdbc.queryForMap("SELECT * FROM deliveries WHERE id=?", delivery),
        jdbc.queryForList("SELECT * FROM notifications WHERE member_id=? AND reference_type='DELIVERY' AND reference_id=? ORDER BY id", order.member(), delivery),
        jdbc.queryForList("SELECT * FROM admin_audit_logs WHERE admin_id=? AND target_type='DELIVERY' AND target_id=? ORDER BY id", order.admin(), delivery)));
  }

  private enum Transition {
    SHIP("SHIPPED", "ORDER_SHIPPED", "DELIVERY_SHIP"),
    COMPLETE("DELIVERED", "ORDER_DELIVERED", "DELIVERY_COMPLETE"),
    FAIL("FAILED", null, "DELIVERY_FAIL");

    final String status;
    final String notification;
    final String audit;
    Transition(String status, String notification, String audit) {
      this.status = status;
      this.notification = notification;
      this.audit = audit;
    }
  }

  private record Snapshot(Map<String, Object> delivery, List<Map<String, Object>> notifications,
                          List<Map<String, Object>> audits) {}
}
