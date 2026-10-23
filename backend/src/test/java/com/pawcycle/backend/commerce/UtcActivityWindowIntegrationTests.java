package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.cart.application.CartApplicationService;
import com.pawcycle.backend.commerce.payment.persistence.PaymentPersistenceAdapter;
import com.pawcycle.backend.commerce.payment.persistence.PaymentReconciliationPersistenceAdapter;
import com.pawcycle.backend.commerce.wishlist.application.WishlistApplicationService;
import com.pawcycle.backend.recommendation.domain.RecommendationTrendScore;
import com.pawcycle.backend.recommendation.persistence.RecommendationQueryAdapter;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(WishlistUtcTimeIntegrationTests.FixedClock.class)
@Transactional
class UtcActivityWindowIntegrationTests {
  private static final Instant NOW = Instant.parse("2026-10-21T07:00:00.123456Z");
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired WishlistApplicationService wishlist;
  @Autowired CartApplicationService cart;
  @Autowired PaymentPersistenceAdapter payments;
  @Autowired PaymentReconciliationPersistenceAdapter reconciliations;
  @Autowired RecommendationQueryAdapter recommendations;
  private SecondaryReadFixtures fixtures;
  private long member;
  private com.pawcycle.backend.catalog.category.domain.Category category;
  private com.pawcycle.backend.catalog.brand.domain.Brand brand;

  @BeforeEach void fixture() {
    fixtures = new SecondaryReadFixtures(jdbc, entities);
    member = fixtures.member(); category = fixtures.category(true); brand = fixtures.brand(true);
  }

  @Test void cartCreateUpdateNoOpDeleteAndRollbackUseUtcWithUnchangedVersionRules() {
    var sku = fixtures.sku(fixtures.product(category, brand, "DOG", "PUBLIC"), SkuStatus.ACTIVE, 10);
    cart.add(member, sku.getId(), 2); entities.flush(); entities.clear();
    assertCartUtc(1);
    cart.update(member, sku.getId(), 2); entities.flush(); entities.clear(); assertCartUtc(1);
    cart.update(member, sku.getId(), 3); entities.flush(); entities.clear(); assertCartUtc(2);
    cart.delete(member, sku.getId()); entities.flush(); entities.clear(); assertCartUtc(3);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE sku_id=?", Integer.class, sku.getId())).isZero();
  }

  @Test void normalAndReconciliationCartConsumptionBindUtcAndIncrementOnce() {
    var sku = fixtures.sku(fixtures.product(category, brand, "DOG", "PUBLIC"), SkuStatus.ACTIVE, 10);
    cart.add(member, sku.getId(), 5); entities.flush();
    long order = fixtures.order(member, "ONE_TIME", "PAID", new java.math.BigDecimal("123.45"), SecondaryReadFixtures.stamp());
    fixtures.item(order, sku); entities.flush(); entities.clear();
    payments.consumeCart(member, order); entities.flush(); entities.clear(); assertCartUtc(2);
    reconciliations.consumeCart(member, order); entities.flush(); entities.clear(); assertCartUtc(3);
    assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE sku_id=?", Integer.class, sku.getId())).isEqualTo(3);
  }

  @Test void normalPaymentCreatesMissingCartWithUtcNativeBinding() {
    long order = fixtures.order(member, "ONE_TIME", "PAID", new java.math.BigDecimal("123.45"), SecondaryReadFixtures.stamp());
    entities.flush(); payments.consumeCart(member, order); entities.flush(); entities.clear(); assertCartUtc(0);
  }

  @Test void newWishlistSortsByUtcAndDuplicateAddIsNoOpWhileLegacyAmbiguityRemains() {
    long legacy = product(), first = product(), second = product();
    wish(legacy, LocalDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC).plusHours(9));
    wishlist.add(member, first); wishlist.add(member, second); entities.flush(); entities.clear();
    wishlist.add(member, first); entities.flush(); entities.clear();
    assertThat(wishlist.list(member).stream().map(r -> r.productId()).toList()).containsExactly(legacy, second, first);
    assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%f') FROM wishlist_items WHERE member_id=? AND product_id=?", String.class, member, first))
        .isEqualTo("2026-10-21T07:00:00.123456");
    // The historical row remains ahead: no provenance exists to repair its actual event order.
    assertThat(wishlist.list(member).getFirst().createdAt().toInstant()).isEqualTo(NOW.plusSeconds(8 * 3600));
    wishlist.remove(member, first); entities.flush();
    assertThat(wishlist.list(member)).hasSize(2);
  }

  @Test void thirtyDayPopularityUsesUtcRollingBoundaryForWishlistAndCart() {
    jdbc.execute("SET timestamp=" + NOW.getEpochSecond() + ".123456");
    try {
      var cutoff = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30);
      for (long micros : List.of(-1L, 0L, 1L)) {
        long product = product(); wish(product, cutoff.plusNanos(micros * 1000));
        assertThat(recommendations.popularScores(null).getOrDefault(product, 0L)).isEqualTo(micros < 0 ? 0L : 2L);
        var entity = entities.find(com.pawcycle.backend.catalog.product.domain.Product.class, product);
        var sku = fixtures.sku(entity, SkuStatus.ACTIVE, 10);
        cart.add(member, sku.getId(), 1); entities.flush();
        jdbc.update("UPDATE carts SET updated_at=cast(? as datetime(6)) WHERE member_id=?", cutoff.plusNanos(micros * 1000).toString(), member);
        assertThat(recommendations.popularScores(null).getOrDefault(product, 0L)).isEqualTo(micros < 0 ? 0L : 5L);
        entities.clear();
      }
      long legacy = product(); wish(legacy, cutoff.minusHours(1).plusHours(9));
      assertThat(recommendations.popularScores(null).get(legacy)).isEqualTo(2L);
    } finally { jdbc.execute("SET timestamp=0"); }
  }

  @Test void trendUsesSeoulCalendarBoundariesAgainstUtcDatetimeAtMicrosecondPrecision() {
    LocalDate today = LocalDate.of(2026, 10, 21);
    for (int days : List.of(14, 7, 0)) {
      LocalDateTime boundary = today.minusDays(days).atStartOfDay(ZoneId.of("Asia/Seoul"))
          .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
      for (long micros : List.of(-1L, 0L, 1L)) {
        long product = product(); wish(product, boundary.plusNanos(micros * 1000));
        long recent = days == 7 && micros >= 0 || days == 0 && micros < 0 ? 2 : 0;
        long previous = days == 14 && micros >= 0 || days == 7 && micros < 0 ? 2 : 0;
        assertThat(recommendations.trendScores(List.of(product), today).get(product))
            .as("Seoul boundary -%s days %s microseconds", days, micros)
            .isEqualTo(new RecommendationTrendScore(recent, previous));
        long legacy = product();
        wish(legacy, boundary.plusNanos(micros * 1000).plusHours(9));
        // An old Seoul wall-clock representation remains ambiguous; do not repair its raw value.
        assertThat(recommendations.trendScores(List.of(legacy), today).get(legacy))
            .as("unrepaired legacy +9h at Seoul boundary -%s days %s microseconds", days, micros)
            .isEqualTo(new RecommendationTrendScore(days == 7 ? 2 : 0, days == 14 ? 2 : 0));
      }
    }
  }

  private void assertCartUtc(long version) {
    assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%f') FROM carts WHERE member_id=?", String.class, member)).isEqualTo("2026-10-21T07:00:00.123456");
    assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(updated_at,'%Y-%m-%dT%H:%i:%s.%f') FROM carts WHERE member_id=?", String.class, member)).isEqualTo("2026-10-21T07:00:00.123456");
    assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE member_id=?", Long.class, member)).isEqualTo(version);
    assertThat(entities.createQuery("select c.updatedAt from CartEntity c where c.memberId=:id", LocalDateTime.class).setParameter("id", member).getSingleResult())
        .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
  }
  private long product() { var p = fixtures.product(category, brand, "DOG", "PUBLIC"); entities.flush(); return p.getId(); }
  private void wish(long product, LocalDateTime value) {
    jdbc.update("INSERT INTO wishlist_items(member_id,product_id,created_at) VALUES (?,?,cast(? as datetime(6)))", member, product, value.toString());
  }
}
