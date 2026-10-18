package com.pawcycle.backend.commerce.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.order.application.OrderApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles({"test", "local-integration"})
class QuickReorderTypedMappingIntegrationTests {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private PlatformTransactionManager manager;
  @Autowired private ObjectMapper json;
  @Autowired private OrderApplicationService orders;
  @Autowired private jakarta.persistence.EntityManagerFactory emf;
  @Autowired private QuickReorderPersistenceAdapter quickReorders;
  @Autowired private WebApplicationContext context;

  @Test
  void legacyStoredShapesReplayWithExactlyTheSameResultsOrConflictsWithoutCartWrites() {
    Fixture f = fixture(true);
    var fixed = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
    var legacy = new FrozenQuickReorderJdbcReference(jdbc, json, fixed);
    var typed = new QuickReorderPersistenceAdapter(entities, json, fixed);
    for (String payload : List.of(
        "{\"addedItems\":[null,7,\"text\",true,[]],\"skippedItems\":[],\"cartVersion\":1,\"extra\":true}",
        "{\"addedItems\":[{\"skuId\":12.9,\"quantity\":4294967297}],\"skippedItems\":[{\"skuId\":3,\"quantity\":4,\"reason\":null}],\"cartVersion\":1.9}",
        "{}", "[]", "null",
        "{\"addedItems\":[{}],\"skippedItems\":[],\"cartVersion\":1}",
        "{\"addedItems\":[],\"skippedItems\":[null],\"cartVersion\":1}",
        "{\"addedItems\":[],\"skippedItems\":[],\"cartVersion\":\"1\"}")) {
      new TransactionTemplate(manager).executeWithoutResult(status -> {
        String key = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO quick_reorder_idempotency_results(member_id,idempotency_key,source_order_id,response_json,cart_version,created_at) VALUES (?,?,?,?,0,?)",
            f.member(), key, f.order(), payload, Timestamp.from(fixed.instant()));
        String before = replayOutcome(() -> legacy.reorder(f.member(), f.order(), key));
        String after = replayOutcome(() -> typed.reorder(f.member(), f.order(), key));
        assertThat(after).as("stored legacy JSON: %s", payload).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE member_id=?", Integer.class, f.member())).isZero();
        assertThat(json.readTree(stored(f.member(), key))).isEqualTo(json.readTree(payload));
        status.setRollbackOnly();
      });
    }
  }

  @Test
  void serializationFailureKeepsStorageExceptionAndRollsBackEarlierCartWritesForBothProtocols() {
    Fixture f = fixture(true);
    ObjectMapper failing = org.mockito.Mockito.mock(ObjectMapper.class);
    var failure = new IllegalArgumentException("test serialization failure");
    org.mockito.Mockito.when(failing.writeValueAsString(org.mockito.ArgumentMatchers.any())).thenThrow(failure);
    for (boolean frozen : List.of(true, false)) {
      String key = UUID.randomUUID().toString();
      assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
        if (frozen) new FrozenQuickReorderJdbcReference(jdbc, failing, Clock.systemUTC()).reorder(f.member(), f.order(), key);
        else new QuickReorderPersistenceAdapter(entities, failing, Clock.systemUTC()).reorder(f.member(), f.order(), key);
      })).isInstanceOf(IllegalStateException.class).hasMessage("Quick Reorder 결과를 저장할 수 없습니다.").hasCause(failure);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE member_id=?", Integer.class, f.member())).isZero();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quick_reorder_idempotency_results WHERE member_id=? AND idempotency_key=?", Integer.class, f.member(), key)).isZero();
    }
  }

  private String replayOutcome(java.util.function.Supplier<?> action) {
    try {
      return json.writeValueAsString(action.get());
    } catch (CommerceException exception) {
      return exception.status() + ":" + exception.code() + ":" + exception.getMessage();
    }
  }

  @Test
  void typedRowsMatchFrozenJdbcResultStoredJsonAndCartIncludingSkipOrdering() {
    Fixture f = fixture(true);
    var fixed = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
    var legacy = new FrozenQuickReorderJdbcReference(jdbc, json, fixed);
    var typed = new QuickReorderPersistenceAdapter(entities, json, fixed);
    String key = UUID.randomUUID().toString();
    Snapshot before = rollbackSnapshot(f, key, () -> legacy.reorder(f.member(), f.order(), key));
    Snapshot after = rollbackSnapshot(f, key, () -> typed.reorder(f.member(), f.order(), key));
    assertThat(after).isEqualTo(before);
    var result = json.readTree(after.resultJson());
    assertThat(result.get("addedItems").size()).isEqualTo(1);
    assertThat(result.get("skippedItems").size()).isEqualTo(5);
    assertThat(result.get("skippedItems").get(0).get("reason").asText()).isEqualTo("OUT_OF_STOCK");
    assertThat(result.get("skippedItems").get(1).get("reason").asText()).isEqualTo("OUT_OF_STOCK");
    assertThat(result.get("skippedItems").get(2).get("reason").asText()).isEqualTo("SKU_NOT_PURCHASABLE");
    assertThat(result.get("skippedItems").get(3).get("reason").asText()).isEqualTo("SKU_NOT_PURCHASABLE");
    assertThat(result.get("skippedItems").get(4).get("reason").asText()).isEqualTo("SKU_NOT_PURCHASABLE");
    assertThat(result.get("cartVersion").asLong()).isEqualTo(1);
  }

  @Test
  void statementCountsExistingRowLocksMicrosecondsAndRollbackMatchFrozen() {
    Fixture f = fixture(true);
    var fixed = Clock.fixed(Instant.parse("2026-10-14T00:00:00.123456Z"), ZoneOffset.UTC);
    var stamp = Timestamp.from(fixed.instant());
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      jdbc.update("INSERT INTO carts(member_id,created_at,updated_at) VALUES (?,?,?)", f.member(), stamp, stamp);
      long cart = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      jdbc.update("INSERT INTO cart_items(cart_id,sku_id,quantity) VALUES (?,?,1)", cart, f.sku());
    });
    String key = UUID.randomUUID().toString();
    var before = physicalSnapshot(f, key, () -> new FrozenQuickReorderJdbcReference(jdbc, json, fixed).reorder(f.member(), f.order(), key));
    var after = physicalSnapshot(f, key, () -> new QuickReorderPersistenceAdapter(entities, json, fixed).reorder(f.member(), f.order(), key));
    assertThat(after).isEqualTo(before);
    assertThat(after.updatedAt()).isEqualTo(stamp);
    assertThat(after.createdAt()).isEqualTo(stamp);
    assertThat(after.createdAt().getNanos()).isEqualTo(123456000);
    assertThat(quantity(f.member(), f.sku())).isEqualTo(1);
    assertThat(version(f.member())).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quick_reorder_idempotency_results WHERE member_id=?", Long.class, f.member())).isZero();
  }

  @Test
  void sourceEligibilityDoesNotAutoFlushOrDetachDirtyManagedCategory() {
    Fixture f = fixture(true);
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      long categoryId = jdbc.queryForObject("SELECT product.category_id FROM products product JOIN skus sku ON sku.product_id=product.id WHERE sku.id=?", Long.class, f.sku());
      var category = entities.find(Category.class, categoryId);
      category.update(null, null, null, false);
      var result = new QuickReorderPersistenceAdapter(entities, json, Clock.systemUTC()).reorder(f.member(), f.order(), UUID.randomUUID().toString());
      assertThat(result.addedItems()).hasSize(1);
      assertThat(jdbc.queryForObject("SELECT active FROM categories WHERE id=?", Boolean.class, categoryId)).isTrue();
      assertThat(entities.contains(category)).isTrue();
      entities.flush();
      assertThat(jdbc.queryForObject("SELECT active FROM categories WHERE id=?", Boolean.class, categoryId)).isFalse();
      status.setRollbackOnly();
    });
  }

  @Test
  void existingCartSkuUsesUpdateAndReplayPreservesExactStoredLegacyJson() {
    Fixture f = fixture(true);
    String key = UUID.randomUUID().toString();
    var legacy = new FrozenQuickReorderJdbcReference(jdbc, json, Clock.systemUTC());
    new TransactionTemplate(manager).executeWithoutResult(status -> legacy.reorder(f.member(), f.order(), key));
    String stored = stored(f.member(), key);
    var replay = orders.reorder(f.member(), f.order(), key);
    assertThat(json.readTree(json.writeValueAsString(replay))).isEqualTo(json.readTree(stored));
    assertThat(stored(f.member(), key)).isEqualTo(stored);
    assertThat(quantity(f.member(), f.sku())).isEqualTo(2);
    assertThat(version(f.member())).isEqualTo(1);
    orders.reorder(f.member(), f.order(), UUID.randomUUID().toString());
    assertThat(quantity(f.member(), f.sku())).isEqualTo(4);
    assertThat(version(f.member())).isEqualTo(2);
  }

  @Test
  void noEligibleItemsLeavesVersionUnchangedAndOwnershipErrorsDoNotCreateCart() {
    Fixture f = fixture(false);
    assertThat(orders.reorder(f.member(), f.order(), "skip-" + UUID.randomUUID()).cartVersion()).isZero();
    assertThat(version(f.member())).isZero();
    long stranger = new TransactionTemplate(manager).execute(status -> new SecondaryReadFixtures(jdbc, entities).member());
    assertError(() -> orders.reorder(stranger, f.order(), "ownership"), 404, "ORDER_NOT_FOUND");
    assertError(() -> orders.reorder(stranger, Long.MAX_VALUE, "missing-order"), 404, "ORDER_NOT_FOUND");
    assertError(() -> orders.reorder(Long.MAX_VALUE, f.order(), "missing-member"), 404, "MEMBER_NOT_FOUND");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE member_id=?", Integer.class, stranger)).isZero();
  }

  @Test
  void firstCartCompetitorPhysicallyWaitsOnMemberThenReplaysForBothImplementations() throws Exception {
    for (boolean frozen : List.of(true, false)) {
      Fixture f = fixture(true);
      String key = UUID.randomUUID().toString();
      var connection = new java.util.concurrent.atomic.AtomicLong();
      var competitor = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<String>>();
      var transaction = new TransactionTemplate(manager);
      transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
      var fixed = Clock.fixed(Instant.parse("2026-10-14T00:00:00.123456Z"), ZoneOffset.UTC);
      java.util.function.Supplier<String> reorder = () -> json.writeValueAsString(frozen
          ? new FrozenQuickReorderJdbcReference(jdbc, json, fixed).reorder(f.member(), f.order(), key)
          : new QuickReorderPersistenceAdapter(entities, json, fixed).reorder(f.member(), f.order(), key));
      try (var executor = Executors.newSingleThreadExecutor()) {
        String holder = transaction.execute(status -> {
          String result = reorder.get();
          competitor.set(executor.submit(() -> transaction.execute(other -> {
            connection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
            return reorder.get();
          })));
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
          int waiting = 0;
          while (waiting == 0 && System.nanoTime() < deadline) {
            waiting = jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits waiters JOIN performance_schema.data_locks blocker ON blocker.ENGINE_LOCK_ID=waiters.BLOCKING_ENGINE_LOCK_ID AND blocker.ENGINE=waiters.ENGINE JOIN performance_schema.threads requester ON requester.THREAD_ID=waiters.REQUESTING_THREAD_ID WHERE requester.PROCESSLIST_ID=? AND blocker.OBJECT_SCHEMA=DATABASE() AND blocker.OBJECT_NAME='members' AND blocker.INDEX_NAME='PRIMARY' AND blocker.LOCK_DATA=?", Integer.class, connection.get(), Long.toString(f.member()));
            if (waiting == 0) {
              try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
          }
          assertThat(waiting).as("observed member row wait; frozen=%s", frozen).isPositive();
          return result;
        });
        assertThat(competitor.get().get(15, TimeUnit.SECONDS)).isEqualTo(holder);
      }
      assertThat(quantity(f.member(), f.sku())).isEqualTo(2);
      assertThat(version(f.member())).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE member_id=?", Long.class, f.member())).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quick_reorder_idempotency_results WHERE member_id=?", Long.class, f.member())).isEqualTo(1);
    }
  }

  @Test
  void simultaneousFirstCartSameKeyRequestsReplayOneMutation() throws Exception {
    Fixture f = fixture(true);
    String key = UUID.randomUUID().toString();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> concurrentReorder(f, key, ready, start));
      var second = executor.submit(() -> concurrentReorder(f, key, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
    }
    assertThat(quantity(f.member(), f.sku())).isEqualTo(2);
    assertThat(version(f.member())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE member_id=?", Integer.class, f.member())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quick_reorder_idempotency_results WHERE member_id=?", Integer.class, f.member())).isEqualTo(1);
  }

  @Test
  void httpReplayMatchesLegacyJsonAndDifferentSourceReturns409() throws Exception {
    Fixture f = fixture(true);
    var auth = new UsernamePasswordAuthenticationToken(
        new AuthenticatedMemberPrincipal(f.member()), null,
        List.of(new SimpleGrantedAuthority("ROLE_USER")));
    var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    String key = UUID.randomUUID().toString();
    String url = "/api/orders/" + f.order() + "/reorder";
    String body = mvc.perform(post(url).with(authentication(auth)).with(csrf()).header("Idempotency-Key", key))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String again = mvc.perform(post(url).with(authentication(auth)).with(csrf()).header("Idempotency-Key", key))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(again).isEqualTo(body);
    assertThat(json.readTree(body)).isEqualTo(json.readTree(stored(f.member(), key)));
    mvc.perform(post("/api/orders/" + (f.order() + 1000000) + "/reorder")
        .with(authentication(auth)).with(csrf()).header("Idempotency-Key", key)).andExpect(status().isConflict());
  }

  @Test
  void responseFactoryAfterCommitKeepsStoredLegacyJsonReplaySqlAndDetachedContract() {
    Fixture f = fixture(true); String key = "boundary410-" + UUID.randomUUID();
    var frozen = new com.pawcycle.backend.commerce.order.application.FrozenOrderApplicationService(
        org.mockito.Mockito.mock(OrderPersistenceAdapter.class), quickReorders, manager, Clock.systemUTC(), 7);
    var expected = frozen.reorder(f.member(), f.order(), key);
    String storedJson = stored(f.member(), key); long previousVersion = version(f.member());
    var stats = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    stats.clear(); var expectedReplay = frozen.reorder(f.member(), f.order(), key); long replaySql = stats.getPrepareStatementCount();
    stats.clear(); var view = orders.reorder(f.member(), f.order(), key);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(replaySql).isEqualTo(2);
    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(emf)).isFalse(); stats.clear();
    var dto = com.pawcycle.backend.commerce.order.api.OrderReorderResponse.from(view);
    assertThat(dto).isEqualTo(expectedReplay).isEqualTo(expected);
    assertThat(json.writeValueAsString(dto)).isEqualTo(json.writeValueAsString(expected));
    assertThat(stats.getPrepareStatementCount()).isZero(); assertThat(stats.getEntityLoadCount()).isZero();
    assertThat(stored(f.member(), key)).isEqualTo(storedJson);
    assertThat(version(f.member())).isEqualTo(previousVersion);
    System.out.println("REORDER_RESPONSE_SQL_PARITY replay=" + replaySql + " detached=0 entityLoads=0 storedJsonUnchanged=true");
  }

  private String concurrentReorder(Fixture f, String key, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
    return json.writeValueAsString(orders.reorder(f.member(), f.order(), key));
  }

  private Snapshot rollbackSnapshot(Fixture f, String key, java.util.function.Supplier<?> action) {
    return new TransactionTemplate(manager).execute(status -> {
      Object result = action.get();
      var snapshot = new Snapshot(json.writeValueAsString(result), stored(f.member(), key),
          jdbc.queryForList("SELECT item.sku_id,item.quantity FROM cart_items item JOIN carts cart ON cart.id=item.cart_id WHERE cart.member_id=? ORDER BY item.sku_id", f.member()), version(f.member()));
      status.setRollbackOnly();
      return snapshot;
    });
  }

  private PhysicalSnapshot physicalSnapshot(Fixture f, String key, java.util.function.Supplier<?> action) {
    var transaction = new TransactionTemplate(manager);
    transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return transaction.execute(status -> {
      assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
      var before = statementCounters();
      String result = json.writeValueAsString(action.get());
      var counts = statementCounters(); counts.replaceAll((name, value) -> value - before.get(name));
      var locks = jdbc.queryForList("SELECT OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_DATA FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE() AND THREAD_ID=(SELECT THREAD_ID FROM performance_schema.threads WHERE PROCESSLIST_ID=CONNECTION_ID()) ORDER BY OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_DATA");
      assertThat(locks).isNotEmpty();
      var snapshot = new PhysicalSnapshot(result, stored(f.member(), key), counts, locks,
          jdbc.queryForObject("SELECT updated_at FROM carts WHERE member_id=?", Timestamp.class, f.member()),
          jdbc.queryForObject("SELECT created_at FROM quick_reorder_idempotency_results WHERE member_id=? AND idempotency_key=?", Timestamp.class, f.member(), key));
      status.setRollbackOnly(); return snapshot;
    });
  }

  private java.util.Map<String, Long> statementCounters() {
    var counts = new java.util.LinkedHashMap<String, Long>();
    jdbc.query("SHOW SESSION STATUS WHERE Variable_name IN ('Com_select','Com_insert','Com_insert_select','Com_update','Com_update_multi')",
        (org.springframework.jdbc.core.RowCallbackHandler) rs -> counts.put(rs.getString(1), rs.getLong(2)));
    return counts;
  }

  private Fixture fixture(boolean eligible) {
    return new TransactionTemplate(manager).execute(status -> {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      long member = fixtures.member();
      long order = fixtures.order(member, "ONE_TIME", "PAID", BigDecimal.ONE, SecondaryReadFixtures.stamp());
      var brand = fixtures.brand(true);
      var category = fixtures.category(true);
      var product = fixtures.product(category, brand, "DOG", "PUBLIC");
      var good = fixtures.sku(product, eligible ? SkuStatus.ACTIVE : SkuStatus.INACTIVE, 10);
      // Deliberately insert source items in an order different from SKU ids.
      var absent = fixtures.sku(product, SkuStatus.ACTIVE, null);
      var low = fixtures.sku(product, SkuStatus.ACTIVE, 1);
      var inactive = fixtures.sku(product, SkuStatus.INACTIVE, 10);
      var hidden = fixtures.sku(fixtures.product(category, brand, "DOG", "INACTIVE"), SkuStatus.ACTIVE, 10);
      var disabled = fixtures.sku(fixtures.product(fixtures.category(false), brand, "DOG", "PUBLIC"), SkuStatus.ACTIVE, 10);
      for (var sku : List.of(absent, low, inactive, hidden, disabled, good)) {
        fixtures.item(order, sku);
        jdbc.update("UPDATE order_items SET quantity=2 WHERE order_id=? AND sku_id=?", order, sku.getId());
      }
      return new Fixture(member, order, good.getId());
    });
  }

  private void assertError(Runnable call, int status, String code) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(CommerceException.class, e -> {
      assertThat(e.status()).isEqualTo(status);
      assertThat(e.code()).isEqualTo(code);
    });
  }
  private String stored(long member, String key) { return jdbc.queryForObject("SELECT response_json FROM quick_reorder_idempotency_results WHERE member_id=? AND idempotency_key=?", String.class, member, key); }
  private long version(long member) { return jdbc.queryForObject("SELECT version FROM carts WHERE member_id=?", Long.class, member); }
  private int quantity(long member, long sku) { return jdbc.queryForObject("SELECT item.quantity FROM cart_items item JOIN carts cart ON cart.id=item.cart_id WHERE cart.member_id=? AND item.sku_id=?", Integer.class, member, sku); }
  private record Fixture(long member, long order, long sku) {}
  private record Snapshot(String resultJson, String storedJson, List<java.util.Map<String, Object>> items, long version) {}
  private record PhysicalSnapshot(String resultJson, String storedJson, java.util.Map<String, Long> counts,
      List<java.util.Map<String, Object>> locks, Timestamp updatedAt, Timestamp createdAt) {}
}
