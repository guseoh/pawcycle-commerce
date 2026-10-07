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
