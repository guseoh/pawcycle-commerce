package com.pawcycle.backend.commerce.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.order.application.OrderApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class QuickReorderTypedMappingIntegrationTests {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private PlatformTransactionManager manager;
  @Autowired private ObjectMapper json;
  @Autowired private OrderApplicationService orders;
  @Autowired private WebApplicationContext context;

  @Test
  void typedRowsMatchFrozenJdbcResultStoredJsonAndCartIncludingSkipOrdering() {
    Fixture f = fixture(true);
    var fixed = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
    var legacy = new FrozenQuickReorderJdbcReference(jdbc, json, fixed);
    var typed = new QuickReorderPersistenceAdapter(jdbc, json, fixed);
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
}
