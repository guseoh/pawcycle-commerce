package com.pawcycle.backend.commerce.notification.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.notification.application.NotificationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.support.AfterSalesFixtures;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class NotificationWriteProtocolIntegrationTests {
  private static final Instant FIRST = Instant.parse("2026-10-08T00:00:00.123456Z");
  @Autowired private NotificationService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private PlatformTransactionManager manager;
  @Autowired private WebApplicationContext context;
  @Autowired private ObjectMapper json;

  @Test
  void concurrentCreateDeduplicatesAndReplayKeepsOriginalCreationTime() throws Exception {
    long member = member();
    var results = AfterSalesFixtures.race(
        () -> { service.create(member, "ORDER_PAID", "ORDER", 77); return true; },
        () -> { service.create(member, "ORDER_PAID", "ORDER", 77); return true; });
    assertThat(results).allSatisfy(r -> assertThat(r.error()).isNull());
    assertThat(service.list(member)).hasSize(1);
    Timestamp created = service.list(member).getFirst().createdAt();
    new TransactionTemplate(manager).executeWithoutResult(status -> at(FIRST.plusSeconds(100)).create(member, "ORDER_PAID", "ORDER", 77));
    assertThat(service.list(member)).hasSize(1);
    assertThat(service.list(member).getFirst().createdAt()).isEqualTo(created);
  }

  @Test
  void repeatedReadFreezesFirstMicrosecondTimestampAndAllReadOnlyChangesUnreadOwnedRows() {
    long member = member();
    long other = member();
    createAt(member, 1);
    createAt(member, 2);
    createAt(other, 1);
    long read = id(member, 1);
    new TransactionTemplate(manager).executeWithoutResult(status -> at(FIRST).markRead(member, read));
    new TransactionTemplate(manager).executeWithoutResult(status -> at(FIRST.plusSeconds(10)).markRead(member, read));
    assertThat(readAt(read)).isEqualTo(Timestamp.from(FIRST));
    new TransactionTemplate(manager).executeWithoutResult(status -> at(FIRST.plusSeconds(20)).markAllRead(member));
    assertThat(readAt(read)).isEqualTo(Timestamp.from(FIRST));
    assertThat(readAt(id(member, 2))).isEqualTo(Timestamp.from(FIRST.plusSeconds(20)));
    assertThat(readAt(id(other, 1))).isNull();
    new TransactionTemplate(manager).executeWithoutResult(status -> at(FIRST.plusSeconds(30)).markAllRead(member));
    assertThat(readAt(id(member, 2))).isEqualTo(Timestamp.from(FIRST.plusSeconds(20)));
  }

  @Test
  void concurrentReadRetainsOneFirstTimestampWithoutFalseNotFound() throws Exception {
    long member = member();
    createAt(member, 1);
    long id = id(member, 1);
    var results = AfterSalesFixtures.race(
        () -> new TransactionTemplate(manager).execute(status -> { at(FIRST).markRead(member, id); return true; }),
        () -> new TransactionTemplate(manager).execute(status -> { at(FIRST.plusSeconds(1)).markRead(member, id); return true; }));
    assertThat(results).allSatisfy(r -> assertThat(r.error()).isNull());
    assertThat(readAt(id)).isIn(Timestamp.from(FIRST), Timestamp.from(FIRST.plusSeconds(1)));
  }

  @Test
  void missingOrOtherMemberReadKeeps404AndLeavesNotificationUnread() {
    long member = member();
    long other = member();
    createAt(member, 1);
    long id = id(member, 1);
    assertThatThrownBy(() -> service.read(other, id)).isInstanceOfSatisfying(CommerceException.class, e -> {
      assertThat(e.status()).isEqualTo(404);
      assertThat(e.code()).isEqualTo("NOTIFICATION_NOT_FOUND");
    });
    assertThatThrownBy(() -> service.read(member, Long.MAX_VALUE)).isInstanceOf(CommerceException.class);
    assertThat(readAt(id)).isNull();
  }

  @Test
  void callerFailureRollsBackCreationAndReadStateInTheSameTransaction() {
    long member = member();
    createAt(member, 1);
    long id = id(member, 1);
    assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
      service.create(member, "ORDER_PAID", "ORDER", 2);
      service.read(member, id);
      throw new IllegalStateException("after notification writes");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(service.list(member)).hasSize(1);
    assertThat(readAt(id)).isNull();
  }

  @Test
  void reminderJoinAndHttpReadContractPreserveNullableContextAndOwnership() throws Exception {
    var f = new AfterSalesFixtures(jdbc, entities, manager);
    var o = f.paidOrder("PREPARING", BigDecimal.ONE);
    long schedule = new TransactionTemplate(manager).execute(status -> {
      jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date) VALUES (?,?,1,2,'2026-10-01','2026-10-15')", o.member(), o.firstSku());
      long subscription = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status) VALUES (?,'2026-10-15','SCHEDULED')", subscription);
      return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    });
    service.create(o.member(), "SUBSCRIPTION_DELIVERY_REMINDER", "SCHEDULE", schedule);
    service.create(o.member(), "ORDER_PAID", "ORDER", o.order());
    var views = service.list(o.member());
    assertThat(views).hasSize(2);
    assertThat(views.getFirst().subscriptionId()).isNull();
    assertThat(views.getFirst().scheduledDate()).isNull();
    assertThat(views.get(1).subscriptionId()).isEqualTo(jdbc.queryForObject("SELECT subscription_id FROM subscription_schedules WHERE id=?", Long.class, schedule));
    assertThat(views.get(1).scheduledDate()).isEqualTo(jdbc.queryForObject("SELECT scheduled_date FROM subscription_schedules WHERE id=?", Timestamp.class, schedule));
    var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    var auth = auth(o.member());
    String body = mvc.perform(get("/api/notifications").with(authentication(auth)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(json.readTree(body)).isEqualTo(json.readTree(json.writeValueAsString(views)));
    long id = views.getFirst().notificationId();
    mvc.perform(patch("/api/notifications/" + id + "/read").with(authentication(auth)).with(csrf())).andExpect(status().isNoContent());
    Timestamp firstRead = readAt(id);
    mvc.perform(patch("/api/notifications/" + id + "/read").with(authentication(auth)).with(csrf())).andExpect(status().isNoContent());
    assertThat(readAt(id)).isEqualTo(firstRead);
    mvc.perform(patch("/api/notifications/" + id + "/read").with(authentication(auth(o.admin()))).with(csrf())).andExpect(status().isNotFound());
    mvc.perform(patch("/api/notifications/read-all").with(authentication(auth)).with(csrf())).andExpect(status().isNoContent());
    assertThat(service.list(o.member())).allSatisfy(v -> assertThat(v.readAt()).isNotNull());
  }

  private UsernamePasswordAuthenticationToken auth(long member) {
    return new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(member), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }
  private NotificationPersistenceAdapter at(Instant instant) { return new NotificationPersistenceAdapter(jdbc, Clock.fixed(instant, ZoneOffset.UTC)); }
  private long member() { return new TransactionTemplate(manager).execute(status -> new SecondaryReadFixtures(jdbc, entities).member()); }
  private void createAt(long member, long reference) { new TransactionTemplate(manager).executeWithoutResult(status -> at(FIRST).create(member, "ORDER_PAID", "ORDER", reference)); }
  private long id(long member, long reference) { return jdbc.queryForObject("SELECT id FROM notifications WHERE member_id=? AND reference_id=?", Long.class, member, reference); }
  private Timestamp readAt(long id) { return jdbc.queryForObject("SELECT read_at FROM notifications WHERE id=?", Timestamp.class, id); }
}
