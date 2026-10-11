package com.pawcycle.backend.interaction.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.pawcycle.backend.support.ReadQueryParity.compare;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.domain.ProductStatus;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.interaction.api.InteractionEventRequest;
import com.pawcycle.backend.interaction.application.InteractionService;
import com.pawcycle.backend.interaction.persistence.InteractionEventPersistenceAdapter.InteractionRecord;
import com.pawcycle.backend.support.AfterSalesFixtures;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class InteractionWriteProtocolIntegrationTests {
  private static final Timestamp FIRST = Timestamp.from(Instant.parse("2026-10-11T23:59:59.123456Z"));
  @Autowired EntityManager entities;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired InteractionEventPersistenceAdapter adapter;
  @Autowired InteractionService service;
  long member, other, product, pet;

  @BeforeEach void fixture() {
    tx().executeWithoutResult(status -> {
      var f = new SecondaryReadFixtures(jdbc, entities);
      member = f.member(); other = f.member();
      var p = f.product(f.category(true), f.brand(true), "DOG", "PUBLIC");
      f.sku(p, SkuStatus.ACTIVE, null); product = p.getId();
      jdbc.update("INSERT INTO pets(member_id,name,pet_type) VALUES (?,'T12B pet','DOG')", member);
      pet = f.lastId();
    });
  }

  @Test void typedExistenceAndNativeInsertMatchFrozenWithoutPrematureFlush() {
    tx().executeWithoutResult(status -> {
      var legacy = new LegacyInteractionEventPersistenceAdapter(jdbc);
      compare(jdbc, () -> legacy.petBelongsToMember(pet, member), () -> adapter.petBelongsToMember(pet, member));
      compare(jdbc, () -> legacy.petBelongsToMember(pet, other), () -> adapter.petBelongsToMember(pet, other));
      compare(jdbc, () -> legacy.petBelongsToMember(Long.MAX_VALUE, member), () -> adapter.petBelongsToMember(Long.MAX_VALUE, member));
      compare(jdbc, () -> legacy.productExists(product), () -> adapter.productExists(product));
      compare(jdbc, () -> legacy.productExists(Long.MAX_VALUE), () -> adapter.productExists(Long.MAX_VALUE));
      entities.find(Product.class, product).transitionTo(ProductStatus.INACTIVE);
      assertThat(adapter.productExists(product)).isTrue();
      var event = event(member, UUID.randomUUID().toString(), "first", FIRST);
      legacy.insert(event);
      var expected = stored(event);
      jdbc.update("DELETE FROM interaction_events WHERE member_id=? AND event_id=?", member, event.eventId());
      assertOneInsert(() -> adapter.insert(event));
      assertThat(stored(event)).isEqualTo(expected);
      adapter.insert(event(member, event.eventId(), "retry", Timestamp.from(FIRST.toInstant().plusSeconds(1))));
      assertThat(stored(event)).isEqualTo(expected);
      assertThat(jdbc.queryForObject("SELECT display_status FROM products WHERE id=?", String.class, product)).isEqualTo("PUBLIC");
      var nullable = new InteractionRecord(member, UUID.randomUUID().toString(), "SEARCH", null, null, null, null, null, FIRST);
      adapter.insert(nullable);
      assertThat(stored(nullable)).isEqualTo(new Stored("SEARCH", null, null, null, null, null, FIRST));
      status.setRollbackOnly();
    });
  }

  @Test void competingIdenticalEventKeepsOneWholeFirstRecordAndUniqueScopeIsPerMember() throws Exception {
    for (boolean frozen : List.of(true, false)) {
      String id = UUID.randomUUID().toString();
      var first = event(member, id, "first", FIRST);
      var second = event(member, id, "second", Timestamp.from(FIRST.toInstant().plusNanos(1000)));
      var legacy = new LegacyInteractionEventPersistenceAdapter(jdbc);
      var outcomes = AfterSalesFixtures.race(
          () -> tx().execute(status -> { if (frozen) legacy.insert(first); else adapter.insert(first); return true; }),
          () -> tx().execute(status -> { if (frozen) legacy.insert(second); else adapter.insert(second); return true; }));
      assertThat(outcomes).allSatisfy(r -> assertThat(r.error()).isNull());
      var winner = stored(first);
      assertThat(winner).isIn(expected(first), expected(second));
      tx().executeWithoutResult(status -> {
        if (frozen) legacy.insert(second); else adapter.insert(second);
        adapter.insert(event(other, id, "other-member", FIRST));
      });
      assertThat(stored(first)).isEqualTo(winner);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interaction_events WHERE event_id=?", Long.class, id)).isEqualTo(2);
    }
  }

  @Test void fiftyEventBatchAndLaterDatabaseFailureRollBackAllWrites() {
    var batch = IntStream.range(0, 50).mapToObj(i -> new InteractionEventRequest(
        UUID.randomUUID().toString(), "PRODUCT_VIEW", product, pet, null, null, null)).toList();
    service.record(member, batch);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interaction_events WHERE member_id=?", Long.class, member)).isEqualTo(50);
    assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
      service.record(other, batch.stream().map(e -> new InteractionEventRequest(e.eventId(), e.type(), e.productId(), null, null, null, null)).toList());
      adapter.insert(new InteractionRecord(other, UUID.randomUUID().toString(), "INVALID", null, null, null, null, null, FIRST));
    })).isInstanceOf(RuntimeException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interaction_events WHERE member_id=?", Long.class, other)).isZero();
  }

  private InteractionRecord event(long owner, String id, String source, Timestamp time) {
    return new InteractionRecord(owner, id, "PRODUCT_VIEW", product, pet, source, null, "{\"hasTextQuery\": true}", time);
  }
  private Stored expected(InteractionRecord event) {
    return new Stored(event.eventType(), event.productId(), event.petId(), event.source(), event.recommendationRequestId(), "{\"hasTextQuery\": true}", event.occurredAt());
  }
  private Stored stored(InteractionRecord event) {
    return jdbc.queryForObject("SELECT event_type,product_id,pet_id,source,recommendation_request_id,context,occurred_at FROM interaction_events WHERE member_id=? AND event_id=?",
        (rs, row) -> new Stored(rs.getString(1), rs.getObject(2, Long.class), rs.getObject(3, Long.class), rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7)), event.memberId(), event.eventId());
  }
  private void assertOneInsert(Runnable write) {
    long before = jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_insert'", (rs, row) -> rs.getLong(2));
    long selects = jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_select'", (rs, row) -> rs.getLong(2));
    write.run();
    long after = jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_insert'", (rs, row) -> rs.getLong(2));
    assertThat(after).isEqualTo(before + 1);
    long selectsAfter = jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Com_select'", (rs, row) -> rs.getLong(2));
    assertThat(selectsAfter).isEqualTo(selects);
  }
  private TransactionTemplate tx() { return new TransactionTemplate(manager); }
  private record Stored(String type, Long product, Long pet, String source, String request, String context, Timestamp time) {}
}
