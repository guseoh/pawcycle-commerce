package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.subscription.model.PetCreateCommand;
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
  @Autowired PetPlanApplicationService petPlans;
  @Autowired RepeatPurchaseApplicationService repeat;
  @Autowired SubscriptionConversionApplicationService conversion;
  @Autowired SubscriptionCycleSuggestionApplicationService cycle;
  @Autowired com.pawcycle.backend.subscription.persistence.RepeatCommerceQueryRepository recurringQueries;
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
    pet = service.createPet(member, new PetCreateCommand("pet", "DOG")).petId();
    subscription = service.createSubscription(member, "initial", request().toCommand()).body().subscriptionId();
  }

  @AfterEach
  void clean() {
    tx().executeWithoutResult(s -> {
      String own = "SELECT id FROM subscriptions WHERE member_id=" + member;
      jdbc.update("DELETE FROM subscription_order_context WHERE order_id IN (SELECT id FROM orders WHERE member_id=?)", member);
      jdbc.update("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE member_id=?)", member);
      jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE member_id=?)", member);
      jdbc.update("DELETE FROM orders WHERE member_id=?", member);
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
    // Seed the pre-refactor API DTO JSON and API-request fingerprint, independently of the new result type.
    jdbc.update("UPDATE subscription_creation_idempotency_results SET response_body=?,payload_fingerprint=? WHERE member_id=? AND idempotency_key='wire-create'",
        oldJson, legacyFingerprint(request()), member);
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
    jdbc.update("UPDATE subscription_command_idempotency_results SET response_body=?,payload_fingerprint=? WHERE member_id=? AND subscription_id=? AND command_type='CHANGE_DELIVERY_CYCLE' AND idempotency_key='wire-command'",
        oldCommandJson, legacyFingerprint(change), member, id);
    service.command(member, id, "pause", "later", "\"1\"", SubscriptionCommandRequest.empty().toCommand());
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
      var result = service.createSubscription(member, "rollback", request().toCommand()); created[0] = result.body().subscriptionId();
      assertThat(result.body()).isExactlyInstanceOf(com.pawcycle.backend.subscription.model.SubscriptionReadModel.Detail.class);
      assertThat(creationJson("rollback")).isEqualTo(databaseJson(json.writeValueAsString(result.body())));
      var command = service.command(member, created[0], "pause", "rollback-command", "\"0\"", SubscriptionCommandRequest.empty().toCommand());
      assertThat(command.body()).isExactlyInstanceOf(com.pawcycle.backend.subscription.model.SubscriptionReadModel.Detail.class);
      assertThat(jdbc.queryForObject("SELECT response_body FROM subscription_command_idempotency_results WHERE member_id=? AND subscription_id=? AND idempotency_key='rollback-command'", String.class, member, created[0]))
          .isEqualTo(databaseJson(json.writeValueAsString(command.body())));
      throw new IllegalStateException("after stored DTO JSON");
    })).isInstanceOf(IllegalStateException.class).hasMessage("after stored DTO JSON");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions WHERE id=?", Integer.class, created[0])).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_creation_idempotency_results WHERE member_id=? AND idempotency_key='rollback'", Integer.class, member)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_command_idempotency_results WHERE member_id=? AND subscription_id=?", Integer.class, member, created[0])).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_command_history WHERE subscription_id=?", Integer.class, created[0])).isZero();
  }

  @Test
  void petAndPlanFrozenSqlCountsDetachedJsonAndHttpContract() throws Exception {
    var old = new FrozenPetPlanApplicationService(store, json, clock);
    var stats = stats();
    stats.clear(); var expectedPet = readTx().execute(s -> old.pet(member, pet)); long petSql = stats.getPrepareStatementCount();
    stats.clear(); var petView = service.pet(member, pet); assertThat(stats.getPrepareStatementCount()).isEqualTo(petSql).isEqualTo(1);
    stats.clear(); var expectedPage = readTx().execute(s -> old.pets(member, 0, 20)); long pageSql = stats.getPrepareStatementCount();
    stats.clear(); var pets = service.pets(member, 0, 20); assertThat(stats.getPrepareStatementCount()).isEqualTo(pageSql).isEqualTo(2);
    stats.clear(); var expectedPlans = readTx().execute(s -> old.plans(member, pet, 0, 20)); long plansSql = stats.getPrepareStatementCount();
    stats.clear(); var plans = service.plans(member, pet, 0, 20); assertThat(stats.getPrepareStatementCount()).isEqualTo(plansSql).isEqualTo(5);
    stats.clear(); var expectedVersion = readTx().execute(s -> old.planVersion(member, pet, version)); long versionSql = stats.getPrepareStatementCount();
    stats.clear(); var planView = service.planVersion(member, pet, version); assertThat(stats.getPrepareStatementCount()).isEqualTo(versionSql).isEqualTo(4);
    unbound(); stats.clear();
    assertThat(json.writeValueAsString(PetResponse.from(petView))).isEqualTo(json.writeValueAsString(expectedPet));
    assertThat(json.writeValueAsString(PageResponse.from(pets, PetResponse::from))).isEqualTo(json.writeValueAsString(expectedPage));
    assertThat(json.writeValueAsString(PageResponse.from(plans, PlanVersionResponse::from))).isEqualTo(json.writeValueAsString(expectedPlans));
    assertThat(json.writeValueAsString(PlanVersionResponse.from(planView))).isEqualTo(json.writeValueAsString(expectedVersion));
    assertThat(stats.getPrepareStatementCount()).isZero(); assertThat(stats.getEntityLoadCount()).isZero();
    String[] paths = {"/api/pets/" + pet, "/api/pets", "/api/subscription-plans?petId=" + pet, "/api/subscription-plan-versions/" + version + "?petId=" + pet};
    Object[] expected = {expectedPet, expectedPage, expectedPlans, expectedVersion};
    for (int i = 0; i < paths.length; i++) {
      assertThat(http.perform(get(paths[i]).with(as(member))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
          .isEqualTo(json.writeValueAsString(expected[i]));
      http.perform(get(paths[i])).andExpect(status().isUnauthorized());
    }
    http.perform(get(paths[0]).with(as(other))).andExpect(status().isNotFound());
    http.perform(get(paths[2]).with(as(other))).andExpect(status().isNotFound());
    System.out.println("PET_PLAN_SQL_PARITY pet=" + petSql + " page=" + pageSql + " plans=" + plansSql + " version=" + versionSql + " detached=0 entityLoads=0");
  }

  @Test
  void petCreateLocationPatchNullableColumnsValidationCsrfAndRollback() throws Exception {
    var created = http.perform(post("/api/pets").with(as(member)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"  new pet  \",\"petType\":\"DOG\"}")).andExpect(status().isCreated()).andReturn().getResponse();
    long id = json.readTree(created.getContentAsString()).get("petId").asLong();
    assertThat(created.getHeader("Location")).isEqualTo("/api/pets/" + id);
    assertThat(created.getContentAsString()).isEqualTo(json.writeValueAsString(new PetResponse(id, "new pet", "DOG", null, null, false)));
    String uri = "/api/pets/" + id;
    http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(uri).with(as(member)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"breed\":\" breed \",\"weightKg\":12.3}")).andExpect(status().isOk());
    var response = http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(uri).with(as(member)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"breed\":null,\"weightKg\":null}")).andExpect(status().isOk()).andReturn().getResponse();
    assertThat(response.getContentAsString()).isEqualTo(json.writeValueAsString(new PetResponse(id, "new pet", "DOG", null, null, false)));
    for (String body : List.of("{}", "{\"name\":null}", "{\"weightKg\":200.01}", "{\"weightKg\":1.001}"))
      http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(uri).with(as(member)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(uri).with(as(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(uri).with(as(member)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    http.perform(post("/api/pets").with(as(member)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \",\"petType\":\"DOG\"}")).andExpect(status().isBadRequest());
    var before = service.pet(member, id); long[] rolledId = new long[1];
    assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
      service.updatePet(member, id, new com.pawcycle.backend.subscription.model.PetUpdateCommand("changed", true, "changed", true, new BigDecimal("99.00"), true));
      rolledId[0] = service.createPet(member, new PetCreateCommand("rollback", "CAT")).petId();
      throw new IllegalStateException("after pet write");
    })).isInstanceOf(IllegalStateException.class).hasMessage("after pet write");
    assertThat(service.pet(member, id)).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pets WHERE id=?", Integer.class, rolledId[0])).isZero();
  }

  @Test
  void recurringFrozenSqlAndDetachedJsonIncludingSuccessfulCycleMatchHttp() throws Exception {
    long order = 0;
    var f = new SecondaryReadFixtures(jdbc, entities);
    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
    for (int n = 0; n < 3; n++) {
      int index = n;
      long[] inserted = new long[1];
      tx().executeWithoutResult(s -> {
        inserted[0] = f.order(member, "ONE_TIME", "PAID", new BigDecimal("123.45"), java.sql.Timestamp.valueOf(today.minusDays(index * 10L).atTime(12, 0)));
        f.payment(inserted[0], "NORMAL", "SUCCEEDED", 1);
        f.item(inserted[0], entities.find(Sku.class, sku));
      });
      order = inserted[0];
    }
    long sourceOrder = order;
    tx().executeWithoutResult(s -> {
      for (int n = 0; n < 3; n++) {
        long paid = f.order(member, "SUBSCRIPTION", "PAID", BigDecimal.ONE, java.sql.Timestamp.valueOf(today.atTime(12, 0)));
        f.payment(paid, "BILLING", "SUCCEEDED", 1);
        LocalDate date = today.minusDays(n * 21L);
        store.insertScheduled(subscription, date);
        long schedule = jdbc.queryForObject("SELECT id FROM subscription_schedules WHERE subscription_id=? AND scheduled_date=?", Long.class, subscription, date);
        jdbc.update("INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,scheduled_date) VALUES (?,?,?,?)", paid, subscription, schedule, date);
      }
    });
    var stats = stats();
    stats.clear(); var oldRepeat = readTx().execute(s -> new FrozenRepeatPurchaseApplicationService(recurringQueries, clock).reorderTiming(member)); long repeatSql = stats.getPrepareStatementCount();
    stats.clear(); var repeatView = repeat.reorderTiming(member); assertThat(stats.getPrepareStatementCount()).isEqualTo(repeatSql).isEqualTo(1);
    stats.clear(); var oldOptions = readTx().execute(s -> new FrozenSubscriptionConversionApplicationService(recurringQueries, clock).subscriptionOptions(member, sourceOrder)); long optionsSql = stats.getPrepareStatementCount();
    stats.clear(); var optionsView = conversion.subscriptionOptions(member, sourceOrder); assertThat(stats.getPrepareStatementCount()).isEqualTo(optionsSql).isEqualTo(6);
    stats.clear(); var oldCycle = readTx().execute(s -> new FrozenSubscriptionCycleSuggestionApplicationService(recurringQueries).cycleSuggestion(member, subscription)); long cycleSql = stats.getPrepareStatementCount();
    stats.clear(); var cycleView = cycle.cycleSuggestion(member, subscription); assertThat(stats.getPrepareStatementCount()).isEqualTo(cycleSql).isEqualTo(4);
    unbound(); stats.clear();
    assertThat(json.writeValueAsString(RepeatPurchaseResponse.from(repeatView))).isEqualTo(json.writeValueAsString(oldRepeat));
    assertThat(json.writeValueAsString(SubscriptionOptionsResponse.from(optionsView))).isEqualTo(json.writeValueAsString(oldOptions));
    assertThat(json.writeValueAsString(SubscriptionCycleSuggestionResponse.from(cycleView))).isEqualTo(json.writeValueAsString(oldCycle));
    assertThat(stats.getPrepareStatementCount()).isZero(); assertThat(stats.getEntityLoadCount()).isZero();
    String[] paths = {"/api/recommendations/reorder-timing", "/api/orders/" + sourceOrder + "/subscription-options", "/api/subscriptions/" + subscription + "/cycle-suggestion"};
    Object[] expected = {oldRepeat, oldOptions, oldCycle};
    for (int n = 0; n < paths.length; n++) {
      assertThat(http.perform(get(paths[n]).with(as(member))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo(json.writeValueAsString(expected[n]));
      http.perform(get(paths[n])).andExpect(status().isUnauthorized());
    }
    http.perform(get(paths[1]).with(as(other))).andExpect(status().isNotFound());
    http.perform(get(paths[2]).with(as(other))).andExpect(status().isNotFound());
    System.out.println("RECURRING_SQL_PARITY reorder=" + repeatSql + " options=" + optionsSql + " cycle=" + cycleSql + " detached=0 entityLoads=0");
  }

  private CreateSubscriptionRequest request() { return new CreateSubscriptionRequest(pet, version, 4); }
  private String legacyFingerprint(Object apiRequest) throws Exception {
    return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(apiRequest)));
  }
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
