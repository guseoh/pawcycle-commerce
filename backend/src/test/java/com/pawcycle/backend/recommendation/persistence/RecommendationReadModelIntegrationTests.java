package com.pawcycle.backend.recommendation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.admin.domain.FacetDefinitionEntity;
import com.pawcycle.backend.catalog.admin.domain.FacetOptionEntity;
import com.pawcycle.backend.catalog.admin.domain.ProductFacetValueEntity;
import com.pawcycle.backend.catalog.brand.domain.Brand;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.recommendation.domain.RecommendationCandidate;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommendationReadModelIntegrationTests {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private RecommendationQueryAdapter queries;
  private LegacyRecommendationQueryAdapter legacy;
  private SecondaryReadFixtures fixtures;
  private long memberId;
  private Category firstCategory;
  private Category secondCategory;
  private Brand brand;
  private Product first;
  private Product second;
  private Sku firstSku;
  private Sku secondSku;

  @BeforeEach
  void setUp() {
    fixtures = new SecondaryReadFixtures(jdbc, entities);
    legacy = new LegacyRecommendationQueryAdapter(jdbc);
    memberId = fixtures.member();
    firstCategory = fixtures.category(true);
    secondCategory = fixtures.category(true);
    brand = fixtures.brand(true);
    first = fixtures.product(firstCategory, brand, "DOG", "PUBLIC");
    second = fixtures.product(secondCategory, brand, "CAT", "PUBLIC");
    firstSku = fixtures.sku(first, SkuStatus.ACTIVE, 10);
    secondSku = fixtures.sku(second, SkuStatus.ACTIVE, 10);
  }

  @Test
  void eligibilityPetTypeBlankOrValueAndIdOrderMatchFrozenJdbcWithoutDuplicates() {
    fixtures.sku(first, SkuStatus.ACTIVE, 20);
    List<Long> excluded = new ArrayList<>();
    excluded.add(ineligible(fixtures.category(false), brand, "PUBLIC", SkuStatus.ACTIVE, 10));
    excluded.add(ineligible(firstCategory, fixtures.brand(false), "PUBLIC", SkuStatus.ACTIVE, 10));
    excluded.add(ineligible(firstCategory, brand, "DRAFT", SkuStatus.ACTIVE, 10));
    excluded.add(ineligible(firstCategory, brand, "PUBLIC", SkuStatus.INACTIVE, 10));
    excluded.add(ineligible(firstCategory, brand, "PUBLIC", SkuStatus.ACTIVE, 0));
    excluded.add(ineligible(firstCategory, brand, "PUBLIC", SkuStatus.ACTIVE, null));
    entities.flush(); entities.clear();
    for (String type : new String[] {null, "", "  ", "DOG", "CAT", "UNKNOWN"}) {
      List<RecommendationCandidate> actual = queries.findPurchasableCandidates(type);
      assertThat(actual).isEqualTo(legacy.findPurchasableCandidates(type));
      assertThat(actual).extracting(RecommendationCandidate::productId).isSorted().doesNotHaveDuplicates()
          .doesNotContainAnyElementsOf(excluded);
    }
    assertThat(queries.findPurchasableCandidates("DOG")).extracting(RecommendationCandidate::productId).contains(first.getId()).doesNotContain(second.getId());
  }

  @Test
  void multipleDefinitionsOptionTiesUnicodeAndEmptyFacetValuesKeepTheirOrder() {
    var definition = new FacetDefinitionEntity(SecondaryReadFixtures.unique(), "첫 정의");
    var later = new FacetDefinitionEntity(SecondaryReadFixtures.unique(), "두 번째");
    entities.persist(definition); entities.persist(later);
    var firstTie = new FacetOptionEntity(definition, "한글:값", 2);
    var secondTie = new FacetOptionEntity(definition, "", 2);
    var earlier = new FacetOptionEntity(definition, "낮은 순서", 1);
    var other = new FacetOptionEntity(later, "다른 정의", 0);
    for (var option : List.of(firstTie, secondTie, earlier, other)) entities.persist(option);
    for (var option : List.of(other, secondTie, earlier, firstTie)) entities.persist(new ProductFacetValueEntity(first, option));
    entities.persist(new ProductFacetValueEntity(second, other));
    entities.flush(); entities.clear();
    assertThat(queries.findPurchasableCandidates(null)).isEqualTo(legacy.findPurchasableCandidates(null));
    assertThat(candidate(first).facets()).containsExactly(definition.getKey() + ":낮은 순서",
        definition.getKey() + ":한글:값", definition.getKey() + ":", later.getKey() + ":다른 정의");
    assertThat(candidate(second).facets()).containsExactly(later.getKey() + ":다른 정의");
  }

  @Test
  void coPurchaseCountsDistinctOrdersWhileCategoryRankingKeepsJoinMultiplicityAndTies() {
    Sku anotherSource = fixtures.sku(first, SkuStatus.ACTIVE, 10);
    Sku anotherOther = fixtures.sku(second, SkuStatus.ACTIVE, 10);
    long firstOrder = paid("ONE_TIME");
    for (Sku sku : List.of(firstSku, anotherSource, secondSku, anotherOther)) fixtures.item(firstOrder, sku);
    long secondOrder = paid("ONE_TIME");
    fixtures.item(secondOrder, firstSku); fixtures.item(secondOrder, secondSku);
    long subscriptionOrder = paid("SUBSCRIPTION");
    fixtures.item(subscriptionOrder, firstSku); fixtures.item(subscriptionOrder, secondSku);
    long unsuccessful = fixtures.order(memberId, "ONE_TIME", "PAID", new BigDecimal("123.45"), SecondaryReadFixtures.stamp());
    fixtures.payment(unsuccessful, "NORMAL", "FAILED", 1);
    fixtures.item(unsuccessful, firstSku); fixtures.item(unsuccessful, secondSku);
    assertThat(queries.coPurchaseCounts(first.getId())).isEqualTo(legacy.coPurchaseCounts(first.getId()))
        .containsEntry(second.getId(), 2L).doesNotContainKey(first.getId());
    assertThat(queries.coPurchaseCounts(Long.MAX_VALUE)).isEqualTo(legacy.coPurchaseCounts(Long.MAX_VALUE)).isEmpty();
    assertThat(queries.purchaseCategorySlugs(memberId)).isEqualTo(legacy.purchaseCategorySlugs(memberId))
        .containsExactly(firstCategory.getSlug(), secondCategory.getSlug());
    assertThat(queries.purchaseCategorySlugs(Long.MAX_VALUE)).isEmpty();
    wish(memberId, first); wish(memberId, second);
    wish(fixtures.member(), second);
    assertThat(queries.wishlistCategorySlugs(memberId)).isEqualTo(legacy.wishlistCategorySlugs(memberId))
        .containsExactly(firstCategory.getSlug(), secondCategory.getSlug());
    Product additional = fixtures.product(secondCategory, brand, "DOG", "PUBLIC");
    entities.flush(); wish(memberId, additional);
    assertThat(queries.wishlistCategorySlugs(memberId)).isEqualTo(legacy.wishlistCategorySlugs(memberId))
        .containsExactly(secondCategory.getSlug(), firstCategory.getSlug());
    assertThat(queries.wishlistCategorySlugs(Long.MAX_VALUE)).isEmpty();
  }

  @Test
  void retainedPopularSignalsStillAssembleTheSameFinalCandidates() {
    long order = paid("ONE_TIME");
    fixtures.item(order, firstSku);
    jdbc.update("UPDATE orders SET paid_at=UTC_TIMESTAMP(6) WHERE id=?", order);
    jdbc.update("INSERT INTO carts(member_id,version,created_at,updated_at) VALUES (?,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", memberId);
    jdbc.update("INSERT INTO cart_items(cart_id,sku_id,quantity) SELECT id,?,1 FROM carts WHERE member_id=?", firstSku.getId(), memberId);
    wish(memberId, first);
    for (String type : List.of("RECOMMENDATION_CLICK", "PRODUCT_VIEW")) jdbc.update(
        "INSERT INTO interaction_events(event_id,member_id,product_id,event_type,recommendation_request_id,occurred_at,context) VALUES (?,?,?,?,?,UTC_TIMESTAMP(6),'{}')",
        UUID.randomUUID().toString(), memberId, first.getId(), type, UUID.randomUUID().toString());
    assertThat(queries.popularScores(null)).isEqualTo(legacy.popularScores(null)).containsEntry(first.getId(), 13L);
    assertThat(queries.findPurchasableCandidates(null)).isEqualTo(legacy.findPurchasableCandidates(null));
    assertThat(candidate(first).popularScore()).isEqualTo(13);
    assertThat(queries.findPurchasableCandidates("DOG")).isEqualTo(legacy.findPurchasableCandidates("DOG"));
  }

  @Test
  void remainingSignalsJsonOwnershipAndSubscriptionAggregatesMatchFrozenJdbcAtMicrosecondBoundaries() {
    long pet = pet(memberId, "DOG");
    long foreignPet = pet(fixtures.member(), "DOG");
    long active = subscription(pet, "ACTIVE", true);
    subscription(pet, "PAUSED", true);
    subscription(pet, "ACTIVE", false);
    subscription(foreignPet, "ACTIVE", true);
    var definition = new FacetDefinitionEntity(SecondaryReadFixtures.unique(), "Facet");
    entities.persist(definition);
    var option = new FacetOptionEntity(definition, "unicode:값", 0);
    entities.persist(option);
    entities.persist(new ProductFacetValueEntity(first, option));
    entities.flush(); entities.clear();
    java.time.LocalDateTime now = java.time.LocalDateTime.of(2026,9,18,12,34,56,123456000);
    jdbc.execute("SET timestamp=" + now.toEpochSecond(java.time.ZoneOffset.UTC) + ".123456");
    try {
      Sku another = fixtures.sku(first, SkuStatus.ACTIVE, 10);
      jdbc.update("INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) SELECT current_snapshot_id,?,1 FROM subscriptions WHERE id=?", another.getId(), active);
      for (int offset : List.of(-1,0,1)) {
        long order = paid("ONE_TIME"); fixtures.item(order, firstSku); fixtures.item(order, another);
        jdbc.update("UPDATE orders SET paid_at=? WHERE id=?", now.minusDays(180).plusNanos(offset*1000L), order);
        for (String type : List.of("PRODUCT_VIEW","RECOMMENDATION_CLICK","PRODUCT_IMPRESSION","RECOMMENDATION_IMPRESSION"))
          event(first.getId(), type, now.minusDays(30).plusNanos(offset*1000L), "{}");
        event(null, "FILTER", now.minusDays(30).plusNanos(offset*1000L),
            "{\"category\":\"" + firstCategory.getSlug() + "\",\"brand\":\"" + brand.getSlug()
                + "\",\"facets\":[\"" + definition.getKey() + ":unicode:값\",\"duplicate\",\"duplicate\",null]}");
      }
      event(null, "FILTER", now, "{}");
      event(null, "FILTER", now, "{\"category\":null,\"brand\":\"\",\"facets\":[]}");
      event(null, "FILTER", now, "{\"category\":123,\"brand\":true,\"facets\":[\"한글\",\"\"]}");
      event(null, "PRODUCT_VIEW", now, "{}");
      wish(memberId, first); wish(memberId, second);
      for (long candidatePet : List.of(pet, foreignPet, Long.MAX_VALUE))
        com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
            () -> legacy.findOwnedPetType(memberId, candidatePet), () -> queries.findOwnedPetType(memberId, candidatePet));
      for (long candidatePet : List.of(pet, foreignPet, Long.MAX_VALUE)) {
        com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
            () -> legacy.activeSubscriptionProductIds(memberId, candidatePet), () -> queries.activeSubscriptionProductIds(memberId, candidatePet));
        com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
            () -> legacy.subscriptionCategorySlugs(memberId, candidatePet), () -> queries.subscriptionCategorySlugs(memberId, candidatePet));
        com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
            () -> legacy.memberSignals(memberId, candidatePet), () -> queries.memberSignals(memberId, candidatePet));
      }
      assertThat(com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
          () -> legacy.exposedProductIds(memberId, 30), () -> queries.exposedProductIds(memberId, 30))).contains(first.getId());
      com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
          () -> legacy.memberSignals(Long.MAX_VALUE, pet), () -> queries.memberSignals(Long.MAX_VALUE, pet));
      for (String type : new String[] {null,"", "  ","DOG","CAT","UNKNOWN"})
        com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
            () -> legacy.popularScores(type), () -> queries.popularScores(type));
    } finally { jdbc.execute("SET timestamp=0"); }
  }

  @Test
  void trendUnionKeepsAllRowsWhileFrozenJdbcDocumentsTheApprovedFirstRowCorrection() {
    java.time.LocalDate today = java.time.LocalDate.of(2026,9,18);
    for (java.time.LocalDateTime at : List.of(today.minusDays(14).atStartOfDay().minusNanos(1000),
        today.minusDays(14).atStartOfDay(), today.minusDays(7).atStartOfDay().minusNanos(1000),
        today.minusDays(7).atStartOfDay(), today.atStartOfDay().minusNanos(1000), today.atStartOfDay())) {
      long order = paid("ONE_TIME"); fixtures.item(order, firstSku);
      fixtures.payment(order, "NORMAL", "FAILED", 2);
      jdbc.update("UPDATE orders SET paid_at=? WHERE id=?", at, order);
      event(first.getId(), "RECOMMENDATION_CLICK", at, "{}");
      event(first.getId(), "PRODUCT_VIEW", at, "{}");
    }
    wish(memberId, first);
    jdbc.update("UPDATE wishlist_items SET created_at=? WHERE member_id=? AND product_id=?",
        today.minusDays(7), memberId, first.getId());
    long otherOrder = paid("SUBSCRIPTION"); fixtures.item(otherOrder, secondSku);
    jdbc.update("UPDATE orders SET paid_at=? WHERE id=?", today.minusDays(1), otherOrder);
    Product noActivity = fixtures.product(firstCategory, brand, "DOG", "PUBLIC");
    entities.flush();
    var firstScore = new com.pawcycle.backend.recommendation.domain.RecommendationTrendScore(18,16);
    var secondScore = new com.pawcycle.backend.recommendation.domain.RecommendationTrendScore(5,0);
    assertThat(legacy.trendScores(List.of(first.getId()), today)).isEmpty();
    assertThat(com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
        () -> trendOracle(List.of(first.getId()), today),
        () -> queries.trendScores(List.of(first.getId()), today)))
        .containsExactlyEntriesOf(java.util.Map.of(first.getId(), firstScore));
    List<Long> ids = List.of(first.getId(), second.getId(), first.getId(), noActivity.getId(), Long.MAX_VALUE);
    var expected = java.util.Map.of(first.getId(), firstScore, second.getId(), secondScore);
    assertThat(com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
        () -> trendOracle(ids, today), () -> queries.trendScores(ids, today))).isEqualTo(expected);
    // Frozen reference is unchanged: exactly one SQL group was lost, all remaining scores agree.
    var frozen = legacy.trendScores(ids, today);
    assertThat(frozen).hasSize(expected.size() - 1);
    assertThat(expected).containsAllEntriesOf(frozen);
    com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
        () -> legacy.trendScores(List.of(), today), () -> queries.trendScores(List.of(), today));
    assertThat(com.pawcycle.backend.support.ReadQueryParity.compare(jdbc,
        () -> trendOracle(List.of(noActivity.getId()), today),
        () -> queries.trendScores(List.of(noActivity.getId()), today))).isEmpty();
  }

  // Normal row-by-row JDBC Oracle: same weights/windows, without advancing the callback cursor.
  private java.util.Map<Long, com.pawcycle.backend.recommendation.domain.RecommendationTrendScore> trendOracle(
      List<Long> ids, java.time.LocalDate today) {
    if (ids.isEmpty()) return java.util.Map.of();
    String sql = """
        SELECT a.product_id,
          COALESCE(SUM(CASE WHEN a.activity_at>=? AND a.activity_at<? THEN a.weight ELSE 0 END),0),
          COALESCE(SUM(CASE WHEN a.activity_at>=? AND a.activity_at<? THEN a.weight ELSE 0 END),0)
        FROM (
          SELECT sku.product_id,o.paid_at activity_at,5 weight FROM orders o
          JOIN payments p ON p.order_id=o.id AND p.status='SUCCEEDED'
          JOIN order_items oi ON oi.order_id=o.id JOIN skus sku ON sku.id=oi.sku_id WHERE o.status='PAID'
          UNION ALL SELECT w.product_id,w.created_at,2 FROM wishlist_items w
          UNION ALL SELECT e.product_id,e.occurred_at,2 FROM interaction_events e WHERE e.event_type='RECOMMENDATION_CLICK'
          UNION ALL SELECT e.product_id,e.occurred_at,1 FROM interaction_events e WHERE e.event_type='PRODUCT_VIEW'
        ) a WHERE a.product_id IN (
        """ + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ") GROUP BY a.product_id";
    var arguments = new java.util.ArrayList<Object>(List.of(today.minusDays(7), today, today.minusDays(14), today.minusDays(7)));
    arguments.addAll(ids);
    var rows = jdbc.query(sql, (rs, rowNumber) -> new TrendOracleRow(rs.getLong(1), rs.getLong(2), rs.getLong(3)), arguments.toArray());
    var result = new java.util.HashMap<Long, com.pawcycle.backend.recommendation.domain.RecommendationTrendScore>();
    for (var row : rows) result.put(row.id(), new com.pawcycle.backend.recommendation.domain.RecommendationTrendScore(row.recent(),row.previous()));
    return result;
  }

  private record TrendOracleRow(long id, long recent, long previous) {}

  private long pet(long owner, String type) {
    jdbc.update("INSERT INTO pets(member_id,name,pet_type) VALUES (?,'Fixture',?)", owner, type);
    return fixtures.lastId();
  }

  private long subscription(long pet, String status, boolean runtime) {
    jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES ('Fixture','DOG',true)");
    long plan = fixtures.lastId();
    jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw) VALUES (?,1000)", plan);
    long version = fixtures.lastId();
    jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,pet_id,runtime_managed,status) VALUES (?,?,1,4,'2026-09-01','2026-09-30',?,?,?)", memberId, firstSku.getId(), pet, runtime, status);
    long id = fixtures.lastId();
    jdbc.update("INSERT INTO subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks) VALUES (?,?,1000,4)", id, version);
    long snapshot = fixtures.lastId();
    jdbc.update("INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) VALUES (?,?,1)", snapshot, firstSku.getId());
    jdbc.update("UPDATE subscriptions SET current_snapshot_id=? WHERE id=?", snapshot, id);
    return id;
  }

  private void event(Long product, String type, java.time.LocalDateTime at, String context) {
    jdbc.update("INSERT INTO interaction_events(event_id,member_id,product_id,event_type,recommendation_request_id,occurred_at,context) VALUES (?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(), memberId, product, type, UUID.randomUUID().toString(), at, context);
  }

  private long ineligible(Category category, Brand owner, String display, SkuStatus status, Integer inventory) {
    Product product = fixtures.product(category, owner, "DOG", display);
    fixtures.sku(product, status, inventory);
    return product.getId();
  }

  private long paid(String source) {
    long order = fixtures.order(memberId, source, "PAID", new BigDecimal("123.45"), SecondaryReadFixtures.stamp());
    fixtures.payment(order, "NORMAL", "SUCCEEDED", 1);
    return order;
  }

  private void wish(long member, Product product) {
    jdbc.update("INSERT INTO wishlist_items(member_id,product_id,created_at) VALUES (?,?,UTC_TIMESTAMP(6))", member, product.getId());
  }

  private RecommendationCandidate candidate(Product product) {
    return queries.findPurchasableCandidates(null).stream().filter(row -> row.productId() == product.getId()).findFirst().orElseThrow();
  }
}
