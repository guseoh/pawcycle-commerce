package com.pawcycle.backend.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.subscription.api.*;
import com.pawcycle.backend.subscription.persistence.SubscriptionAggregatePersistence;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
class SubscriptionReadBoundaryIntegrationTests {
  @Autowired SubscriptionService service;
  @Autowired SubscriptionAggregatePersistence store;
  @Autowired EntityManager entities;
  @Autowired EntityManagerFactory emf;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired Clock clock;
  @Autowired WebApplicationContext web;
  private long member, other, category, brand, product, sku, addon, plan, version, pet, subscription;
  private final String prefix = "read405_" + UUID.randomUUID().toString().substring(0, 8);
  private MockMvc http;

  @BeforeEach
  void fixture() {
    http = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    tx().executeWithoutResult(s -> {
      var f = new SecondaryReadFixtures(jdbc, entities);
      member = f.member(); other = f.member();
      var c = f.category(true); category = c.getId();
      var b = f.brand(true); brand = b.getId();
      var p = f.product(c, b, "DOG", "PUBLIC"); product = p.getId();
      var first = new Sku(p, prefix + "_sku", "base", new BigDecimal("19900.00"), true, 0, SkuStatus.ACTIVE);
      var second = new Sku(p, prefix + "_addon", "addon", new BigDecimal("1234.56"), false, 1, SkuStatus.ACTIVE);
      entities.persist(first); entities.persist(second); entities.flush();
      sku = first.getId(); addon = second.getId();
      jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,'DOG',true)", prefix);
      plan = f.lastId();
      jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,24000,false)", plan);
      version = f.lastId();
      jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,2)", version, sku);
      jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,4),(?,8)", version, version);
      jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", version, plan);
    });
    pet = service.createPet(member, new CreatePetRequest("pet", "DOG")).petId();
    subscription = service.createSubscription(member, "initial", request()).body().subscriptionId();
  }

  @AfterEach
  void clean() {
    tx().executeWithoutResult(s -> {
      String own = "SELECT id FROM subscriptions WHERE member_id=" + member;
      jdbc.update("DELETE FROM pending_plan_changes WHERE subscription_id IN (" + own + ")");
      jdbc.update("DELETE FROM subscription_command_idempotency_results WHERE member_id=?", member);
      jdbc.update("DELETE FROM subscription_creation_idempotency_results WHERE member_id=?", member);
      jdbc.update("DELETE FROM subscription_command_history WHERE subscription_id IN (" + own + ")");
      jdbc.update("DELETE a FROM subscription_schedule_addons a JOIN subscription_schedules s ON s.id=a.schedule_id WHERE s.subscription_id IN (" + own + ")");
      jdbc.update("DELETE FROM subscription_shipping_snapshots WHERE subscription_id IN (" + own + ")");
      jdbc.update("DELETE FROM subscription_schedules WHERE subscription_id IN (" + own + ")");
      jdbc.update("DELETE i FROM subscription_snapshot_items i JOIN subscription_snapshots s ON s.id=i.snapshot_id WHERE s.subscription_id IN (" + own + ")");
      jdbc.update("UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id=?", member);
      jdbc.update("DELETE FROM subscription_snapshots WHERE subscription_id IN (" + own + ")");
      jdbc.update("DELETE FROM subscriptions WHERE member_id=?", member);
      jdbc.update("DELETE FROM pets WHERE member_id IN (?,?)", member, other);
      jdbc.update("UPDATE subscription_plans SET current_plan_version_id=NULL WHERE id=?", plan);
      jdbc.update("DELETE FROM plan_items WHERE plan_version_id=?", version);
      jdbc.update("DELETE FROM plan_version_delivery_cycles WHERE plan_version_id=?", version);
      jdbc.update("DELETE FROM plan_versions WHERE id=?", version);
      jdbc.update("DELETE FROM subscription_plans WHERE id=?", plan);
      jdbc.update("DELETE FROM skus WHERE id IN (?,?)", sku, addon);
      jdbc.update("DELETE FROM products WHERE id=?", product);
      jdbc.update("DELETE FROM brands WHERE id=?", brand);
      jdbc.update("DELETE FROM categories WHERE id=?", category);
      jdbc.update("DELETE FROM members WHERE id IN (?,?)", member, other);
    });
  }

  @Test
  void frozenReadSqlCountsAndDetachedFullGraphJsonAreIdentical() throws Exception {
    tx().executeWithoutResult(s -> {
      long next = store.findNextDeliverySchedule(subscription).orElseThrow().id();
      long pending = store.createSnapshot(subscription, version, 8, 9007199254740991L);
      store.replacePendingPlanChange(subscription, pending, next);
      store.upsertScheduleAddon(next, addon, 3, new BigDecimal("1234.56"));
      for (String type : List.of("PAUSE", "RESUME", "SKIP_NEXT")) jdbc.update(
          "INSERT INTO subscription_command_history(subscription_id,command_type,occurred_at,version_before,version_after) VALUES (?,?,'2026-10-07 23:59:59.123456',0,1)", subscription, type);
    });
    Statistics stats = stats(); stats.clear();
    var expectedPage = readTx().execute(s -> frozen().subscriptions(member, 0, 20));
    long pageSql = stats.getPrepareStatementCount();
    stats.clear(); var page = service.subscriptions(member, 0, 20);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(pageSql).isEqualTo(6);
    assertThat(stats.getEntityLoadCount()).isZero();
    stats.clear(); var expected = readTx().execute(s -> frozen().subscription(member, subscription, 0, 20, 0, 20));
    long detailSql = stats.getPrepareStatementCount();
    stats.clear(); var detail = service.subscription(member, subscription, 0, 20, 0, 20);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(detailSql).isPositive();
    assertThat(stats.getEntityLoadCount()).isZero();
    unbound(); stats.clear();
    assertThat(json.writeValueAsString(PageResponse.from(page, SubscriptionSummaryResponse::from)))
        .isEqualTo(json.writeValueAsString(expectedPage));
    var dto = SubscriptionDetailResponse.from(detail);
    assertThat(json.writeValueAsString(dto)).isEqualTo(json.writeValueAsString(expected.body()));
    assertThat(dto.nextDelivery().addOnTotalKrw()).isEqualTo(new BigDecimal("3703.68"));
    assertThat(dto.nextDelivery().orderTotalKrw()).isEqualTo(new BigDecimal("9007199254744694.68"));
    assertThat(dto.commandHistory().items()).extracting(CommandHistoryResponse::commandType).containsExactly("SKIP_NEXT", "RESUME", "PAUSE");
    assertThat(dto.commandHistory().items().getFirst().occurredAt()).endsWith(".123456+09:00");
    assertThat(stats.getPrepareStatementCount()).isZero(); assertThat(stats.getEntityLoadCount()).isZero();
    System.out.println("SUB_READ_SQL_PARITY page=" + pageSql + " detail=" + detailSql + " detached=0 entityLoads=0");
  }

  @Test
  void getWireJsonEtagEmptyPagesNullablePetScopeAndSecurityMatchFrozenQuery() throws Exception {
    var expected = readTx().execute(s -> frozen().subscription(member, subscription, 0, 20, 0, 20));
    var result = http.perform(get("/api/subscriptions/" + subscription).with(as(member))).andExpect(status().isOk()).andReturn().getResponse();
    assertThat(result.getContentAsString()).isEqualTo(json.writeValueAsString(expected.body()));
    assertThat(result.getHeader("ETag")).isEqualTo(expected.etag());
    assertThat(result.getHeader("Location")).isNull(); assertThat(result.getHeader("Idempotency-Replayed")).isNull();
    jdbc.update("UPDATE subscriptions SET pet_id=NULL WHERE id=?", subscription);
    var page = readTx().execute(s -> frozen().subscriptions(member, 0, 20));
    var listing = http.perform(get("/api/subscriptions").with(as(member))).andExpect(status().isOk()).andReturn().getResponse();
    assertThat(listing.getContentAsString()).isEqualTo(json.writeValueAsString(page));
    var emptyPage = readTx().execute(s -> frozen().subscriptions(member, 99, 1));
    var empty = http.perform(get("/api/subscriptions?page=99&size=1").with(as(member))).andExpect(status().isOk()).andReturn().getResponse();
    assertThat(empty.getContentAsString()).isEqualTo(json.writeValueAsString(emptyPage));
    http.perform(get("/api/subscriptions/" + subscription).with(as(other))).andExpect(status().isNotFound());
    http.perform(get("/api/subscriptions/" + subscription)).andExpect(status().isUnauthorized());
    http.perform(get("/api/subscriptions?page=-1").with(as(member))).andExpect(status().isBadRequest());
    http.perform(post("/api/subscriptions").with(as(member)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request())))
        .andExpect(status().isForbidden());
  }

  @Test
  void freshCreateAndCommandStoreFrozenDtoJsonAndReplayIgnoresLaterVersion() throws Exception {
    var created = http.perform(post("/api/subscriptions").with(as(member)).with(csrf()).header("Idempotency-Key", "wire-create")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request()))).andExpect(status().isCreated()).andReturn().getResponse();
    long id = json.readTree(created.getContentAsString()).get("subscriptionId").asLong();
    var original = readTx().execute(s -> frozen().detail(member, id, 0, 20, 0, 20));
    String oldJson = json.writeValueAsString(original);
    assertThat(created.getContentAsString()).isEqualTo(oldJson);
    assertThat(created.getHeader("ETag")).isEqualTo("\"0\"");
    assertThat(created.getHeader("Location")).isEqualTo("/api/subscriptions/" + id);
    assertThat(creationJson("wire-create")).isEqualTo(databaseJson(oldJson));
    var replay = http.perform(post("/api/subscriptions").with(as(member)).with(csrf()).header("Idempotency-Key", "wire-create")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request()))).andExpect(status().isCreated()).andReturn().getResponse();
    assertThat(replay.getContentAsString()).isEqualTo(oldJson);
    assertThat(replay.getHeader("Idempotency-Replayed")).isEqualTo("true");
    assertThat(replay.getHeader("Location")).isEqualTo(created.getHeader("Location"));
    assertThat(replay.getHeader("ETag")).isEqualTo(created.getHeader("ETag"));
    var change = new SubscriptionCommandRequest(null, null, 8, null, null, null);
    var command = http.perform(post("/api/subscriptions/" + id + "/commands/change-delivery-cycle").with(as(member)).with(csrf())
        .header("Idempotency-Key", "wire-command").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(change))).andExpect(status().isOk()).andReturn().getResponse();
    var frozenCommand = readTx().execute(s -> frozen().detail(member, id, 0, 20, 0, 20));
    String oldCommandJson = json.writeValueAsString(frozenCommand);
    assertThat(command.getContentAsString()).isEqualTo(oldCommandJson);
    assertThat(command.getHeader("ETag")).isEqualTo("\"1\"");
    assertThat(commandJson(id, "wire-command")).isEqualTo(databaseJson(oldCommandJson));
    service.command(member, id, "pause", "later", "\"1\"", SubscriptionCommandRequest.empty());
    var stale = http.perform(post("/api/subscriptions/" + id + "/commands/change-delivery-cycle").with(as(member)).with(csrf())
        .header("Idempotency-Key", "wire-command").header("If-Match", "\"999\"").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(change))).andExpect(status().isOk()).andReturn().getResponse();
    assertThat(stale.getContentAsString()).isEqualTo(oldCommandJson);
    assertThat(stale.getHeader("ETag")).isEqualTo("\"1\"");
    assertThat(stale.getHeader("Idempotency-Replayed")).isEqualTo("true");
    assertThat(commandJson(id, "wire-command")).isEqualTo(databaseJson(oldCommandJson));
    assertThat(jdbc.queryForObject("SELECT version FROM subscriptions WHERE id=?", Long.class, id)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_command_history WHERE subscription_id=?", Integer.class, id)).isEqualTo(2);
  }

  @Test
  void responseJsonReservationsAndMutationRollbackTogetherAfterCompletion() {
    long[] created = new long[1];
    assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
      var result = service.createSubscription(member, "rollback", request()); created[0] = result.body().subscriptionId();
      assertThat(result.body()).isExactlyInstanceOf(SubscriptionDetailResponse.class);
      assertThat(creationJson("rollback")).isEqualTo(databaseJson(json.writeValueAsString(result.body())));
      var command = service.command(member, created[0], "pause", "rollback-command", "\"0\"", SubscriptionCommandRequest.empty());
      assertThat(command.body()).isExactlyInstanceOf(SubscriptionDetailResponse.class);
      assertThat(jdbc.queryForObject("SELECT response_body FROM subscription_command_idempotency_results WHERE member_id=? AND subscription_id=? AND idempotency_key='rollback-command'", String.class, member, created[0]))
          .isEqualTo(databaseJson(json.writeValueAsString(command.body())));
      throw new IllegalStateException("after stored DTO JSON");
    })).isInstanceOf(IllegalStateException.class).hasMessage("after stored DTO JSON");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions WHERE id=?", Integer.class, created[0])).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_creation_idempotency_results WHERE member_id=? AND idempotency_key='rollback'", Integer.class, member)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_command_idempotency_results WHERE member_id=? AND subscription_id=?", Integer.class, member, created[0])).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_command_history WHERE subscription_id=?", Integer.class, created[0])).isZero();
  }

  private CreateSubscriptionRequest request() { return new CreateSubscriptionRequest(pet, version, 4); }
  private String creationJson(String key) { return jdbc.queryForObject("SELECT response_body FROM subscription_creation_idempotency_results WHERE member_id=? AND idempotency_key=?", String.class, member, key); }
  private String commandJson(long id, String key) { return jdbc.queryForObject("SELECT response_body FROM subscription_command_idempotency_results WHERE member_id=? AND subscription_id=? AND command_type='CHANGE_DELIVERY_CYCLE' AND idempotency_key=?", String.class, member, id, key); }
  // MySQL JSON columns canonicalize key order/number representation in the frozen implementation too.
  private String databaseJson(String value) { return jdbc.queryForObject("SELECT CAST(? AS JSON)", String.class, value); }
  private RequestPostProcessor as(long id) { return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(id), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))); }
  private void unbound() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); assertThat(TransactionSynchronizationManager.hasResource(emf)).isFalse(); }
  private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
  private TransactionTemplate readTx() { var tx = tx(); tx.setReadOnly(true); return tx; }
  private Statistics stats() { return emf.unwrap(SessionFactory.class).getStatistics(); }
  private FrozenSubscriptionQueryReference frozen() { return new FrozenSubscriptionQueryReference(store, json, clock); }
}
