package com.pawcycle.backend.commerce.billing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.billing.application.BillingApplicationService;
import com.pawcycle.backend.commerce.billing.infrastructure.toss.TossBillingAdapter;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class BillingPreparationProtocolIntegrationTests {
  private static final Instant NOW = Instant.parse("2026-10-16T00:00:00.123456Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  @Autowired BillingPaymentMethodPreparationRepository preparations;
  @Autowired BillingPaymentMethodRepository methods;
  @Autowired EntityManager entities;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  private final List<Long> members = new ArrayList<>();
  private long member;
  private Long sku, product, category, brand;

  @BeforeEach
  void fixtureMember() {
    member = tx().execute(s -> newMember());
  }

  @AfterEach
  void clean() {
    for (long id : members) {
      jdbc.update("DELETE context FROM subscription_order_context context JOIN orders orders ON orders.id=context.order_id WHERE orders.member_id=?", id);
      jdbc.update("DELETE schedule FROM subscription_schedules schedule JOIN subscriptions subscription ON subscription.id=schedule.subscription_id WHERE subscription.member_id=?", id);
      jdbc.update("DELETE FROM subscriptions WHERE member_id=?", id);
      jdbc.update("DELETE FROM orders WHERE member_id=?", id);
      jdbc.update("DELETE FROM billing_payment_method_preparations WHERE member_id=?", id);
      jdbc.update("DELETE FROM billing_payment_methods WHERE member_id=?", id);
      jdbc.update("DELETE FROM members WHERE id=?", id);
    }
    if (sku != null) {
      jdbc.update("DELETE FROM inventories WHERE sku_id=?", sku);
      jdbc.update("DELETE FROM skus WHERE id=?", sku);
      jdbc.update("DELETE FROM products WHERE id=?", product);
      jdbc.update("DELETE FROM brands WHERE id=?", brand);
      jdbc.update("DELETE FROM categories WHERE id=?", category);
    }
  }

  @Test
  void tenMinuteTtlAndExclusiveMicrosecondExpiryKeepStatusErrorPriority() {
    Instant expiry = NOW.plusSeconds(600);
    for (long micros : List.of(-1L, 0L, 1L)) {
      String token = prepare();
      var stored = preparations.findById(token).orElseThrow();
      assertThat(stored.expiresAt).isEqualTo(LocalDateTime.ofInstant(expiry, ZoneOffset.UTC));
      Clock atBoundary = Clock.fixed(expiry.plusNanos(micros * 1000), ZoneOffset.UTC);
      if (micros < 0) {
        tx().executeWithoutResult(s -> adapter(atBoundary).claim(member, token));
        assertThat(preparations.findById(token).orElseThrow().getStatus()).isEqualTo("PROCESSING");
        error(() -> tx().execute(s -> adapter(Clock.fixed(expiry.plusSeconds(60), ZoneOffset.UTC)).claim(member, token)),
            "BILLING_PREPARATION_IN_PROGRESS");
        error(() -> tx().execute(s -> adapter(CLOCK).claim(Long.MAX_VALUE, token)), "BILLING_PREPARATION_INVALID");
      } else {
        error(() -> tx().execute(s -> adapter(atBoundary).claim(member, token)), "BILLING_PREPARATION_INVALID");
        assertThat(preparations.findById(token).orElseThrow().getStatus()).isEqualTo("READY");
      }
    }
    error(() -> tx().execute(s -> adapter(CLOCK).claim(member, "absent-token")), "BILLING_PREPARATION_INVALID");
  }

  @Test
  void simultaneousClaimsHaveExactlyOneCommittedWinnerAndOneInProgressConflict() throws Exception {
    String token = prepare();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<String> contender = () -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("claim start timeout");
        try {
          tx().executeWithoutResult(s -> adapter(CLOCK).claim(member, token));
          return "CLAIMED";
        } catch (CommerceException failure) {
          assertThat(failure.status()).isEqualTo(409);
          return failure.code();
        }
      };
      var first = executor.submit(contender);
      var second = executor.submit(contender);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("CLAIMED", "BILLING_PREPARATION_IN_PROGRESS");
    }
    var stored = preparations.findById(token).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo("PROCESSING");
    assertThat(stored.claimedAt).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
  }

  @Test
  void providerObservesCommittedClaimOutsideTransactionAndRegisterCommitsBeforeReturn() {
    String token = prepare();
    TossBillingAdapter provider = configuredProvider();
    when(provider.issueBillingKey(anyString(), anyString())).thenAnswer(call -> {
      assertCommittedClaim(token);
      assertThat(methodCount()).isZero();
      return new TossBillingAdapter.BillingKeyResult("fixture-issued-key");
    });
    var app = new BillingApplicationService(adapter(CLOCK), manager, provider);
    app.complete(member, token, "fixture-auth");
    assertThat(preparations.findById(token)).isEmpty();
    assertThat(methodCount()).isOne();
    assertThat(jdbc.queryForObject("SELECT billing_key FROM billing_payment_methods WHERE member_id=? AND status='ACTIVE'", String.class, member))
        .isEqualTo("fixture-issued-key");
    error(() -> app.complete(member, token, "fixture-auth"), "BILLING_PREPARATION_INVALID");
    verify(provider, times(1)).issueBillingKey(anyString(), anyString());
  }

  @Test
  void providerFailureLeavesCommittedProcessingAndDoesNotReissueOnRetry() {
    String token = prepare();
    TossBillingAdapter provider = configuredProvider();
    var failure = new IllegalStateException("fixture provider failure");
    when(provider.issueBillingKey(anyString(), anyString())).thenAnswer(call -> {
      assertCommittedClaim(token);
      throw failure;
    });
    var app = new BillingApplicationService(adapter(CLOCK), manager, provider);
    assertThatThrownBy(() -> app.complete(member, token, "fixture-auth")).isSameAs(failure);
    assertThat(preparations.findById(token).orElseThrow().getStatus()).isEqualTo("PROCESSING");
    assertThat(methodCount()).isZero();
    error(() -> app.complete(member, token, "fixture-auth"), "BILLING_PREPARATION_IN_PROGRESS");
    verify(provider, times(1)).issueBillingKey(anyString(), anyString());
  }

  @Test
  void registerUniqueFailureRollsBackRevocationAndRetainsPreviouslyCommittedClaim() {
    String token = prepare();
    String customer = preparations.findById(token).orElseThrow().getCustomerKey();
    long old = activeMethod(customer);
    TossBillingAdapter provider = configuredProvider();
    when(provider.issueBillingKey(anyString(), anyString())).thenAnswer(call -> {
      assertCommittedClaim(token);
      return new TossBillingAdapter.BillingKeyResult("fixture-conflicting-key");
    });
    var app = new BillingApplicationService(adapter(CLOCK), manager, provider);
    assertThatThrownBy(() -> app.complete(member, token, "fixture-auth")).isInstanceOf(RuntimeException.class);
    assertThat(methodCount()).isOne();
    assertThat(jdbc.queryForObject("SELECT status FROM billing_payment_methods WHERE id=?", String.class, old)).isEqualTo("ACTIVE");
    assertThat(jdbc.queryForObject("SELECT revoked_at FROM billing_payment_methods WHERE id=?", Timestamp.class, old)).isNull();
    assertThat(preparations.findById(token).orElseThrow().getStatus()).isEqualTo("PROCESSING");
    verify(provider, times(1)).issueBillingKey(anyString(), anyString());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void registerFlushDeleteAndExactHoldScopeCommitTogetherOrRollBackTogether(boolean abort) {
    String token = prepare();
    tx().executeWithoutResult(s -> adapter(CLOCK).claim(member, token));
    long old = activeMethod("fixture-old-" + UUID.randomUUID());
    List<Long> schedules = tx().execute(s -> holds());
    var before = states(schedules);
    var injected = new IllegalStateException("fixture failure after final hold statement");
    Runnable register = () -> tx().executeWithoutResult(s -> {
      adapter(CLOCK).register(member, token, "fixture-replacement-key");
      assertThat(preparations.findById(token)).isEmpty();
      var after = states(schedules);
      assertThat(after.getFirst().get("status")).isEqualTo("SCHEDULED");
      assertThat(after.getFirst().get("hold_reason")).isNull();
      assertThat(after.subList(1, after.size())).isEqualTo(before.subList(1, before.size()));
      if (abort) throw injected;
    });
    if (abort) {
      assertThatThrownBy(register::run).isSameAs(injected);
      assertThat(states(schedules)).isEqualTo(before);
      assertThat(methodCount()).isOne();
      assertThat(jdbc.queryForObject("SELECT status FROM billing_payment_methods WHERE id=?", String.class, old)).isEqualTo("ACTIVE");
      assertThat(preparations.findById(token).orElseThrow().getStatus()).isEqualTo("PROCESSING");
    } else {
      register.run();
      assertThat(methodCount()).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT status FROM billing_payment_methods WHERE id=?", String.class, old)).isEqualTo("REVOKED");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_payment_methods WHERE member_id=? AND status='ACTIVE'", Integer.class, member)).isOne();
      assertThat(preparations.findById(token)).isEmpty();
    }
  }

  private List<Long> holds() {
    var f = new SecondaryReadFixtures(jdbc, entities);
    var c = f.category(true); category = c.getId();
    var b = f.brand(true); brand = b.getId();
    var p = f.product(c, b, "DOG", "PUBLIC"); product = p.getId();
    var sk = f.sku(p, SkuStatus.ACTIVE, 10); sku = sk.getId();
    long active = subscription(member, "ACTIVE");
    long paused = subscription(member, "PAUSED");
    long foreign = subscription(newMember(), "ACTIVE");
    long eligible = schedule(active, "2026-11-01", "MISSING_BILLING_METHOD");
    long address = schedule(active, "2026-11-02", "MISSING_SHIPPING_ADDRESS");
    long ordered = schedule(active, "2026-11-03", "MISSING_BILLING_METHOD");
    long heldPaused = schedule(paused, "2026-11-01", "MISSING_BILLING_METHOD");
    long heldForeign = schedule(foreign, "2026-11-01", "MISSING_BILLING_METHOD");
    long otherReason = schedule(active, "2026-11-04", "PAYMENT_RETRY_EXHAUSTED");
    jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,hold_reason) VALUES (?,'2026-11-05','SKIPPED','MISSING_BILLING_METHOD')", active);
    long skipped = f.lastId();
    long order = f.order(member, "SUBSCRIPTION", "PAID", BigDecimal.ONE, Timestamp.from(NOW));
    jdbc.update("INSERT INTO subscription_order_context(order_id,subscription_id,schedule_id,scheduled_date) SELECT ?,subscription_id,id,scheduled_date FROM subscription_schedules WHERE id=?", order, ordered);
    // Flush generated fixture ids before returning to the committed register transaction.
    entities.flush();
    category = c.getId(); brand = b.getId(); product = p.getId();
    return List.of(eligible, address, ordered, heldPaused, heldForeign, otherReason, skipped);
  }

  private long subscription(long owner, String status) {
    jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,status) VALUES (?,?,1,2,'2026-10-01','2026-11-01',?)", owner, sku, status);
    return new SecondaryReadFixtures(jdbc, entities).lastId();
  }

  private long schedule(long subscription, String date, String reason) {
    jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,hold_reason) VALUES (?,?,'HELD',?)", subscription, date, reason);
    return new SecondaryReadFixtures(jdbc, entities).lastId();
  }

  private List<Map<String, Object>> states(List<Long> ids) {
    return ids.stream().map(id -> jdbc.queryForMap("SELECT id,status,hold_reason FROM subscription_schedules WHERE id=?", id)).toList();
  }

  private long activeMethod(String customer) {
    return tx().execute(s -> {
      var method = methods.saveAndFlush(new BillingPaymentMethodEntity(member, customer, "fixture-old-key", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));
      return method.id;
    });
  }

  private void assertCommittedClaim(String token) {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(jdbc.queryForObject("SELECT status FROM billing_payment_method_preparations WHERE prepare_token=?", String.class, token)).isEqualTo("PROCESSING");
  }

  private int methodCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM billing_payment_methods WHERE member_id=?", Integer.class, member);
  }

  private String prepare() {
    return tx().execute(s -> adapter(CLOCK).createPreparation(member));
  }

  private long newMember() {
    long id = new SecondaryReadFixtures(jdbc, entities).member();
    members.add(id);
    return id;
  }

  private TossBillingAdapter configuredProvider() {
    var provider = mock(TossBillingAdapter.class);
    when(provider.isConfigured()).thenReturn(true);
    return provider;
  }

  private BillingPersistenceAdapter adapter(Clock clock) {
    return new BillingPersistenceAdapter(preparations, methods, entities, clock);
  }

  private TransactionTemplate tx() { return new TransactionTemplate(manager); }

  private void error(Runnable call, String code) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(CommerceException.class, error -> {
      assertThat(error.status()).isEqualTo(409);
      assertThat(error.code()).isEqualTo(code);
    });
  }
}
