package com.pawcycle.backend.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.product.application.ProductListView;
import com.pawcycle.backend.catalog.product.application.ProductSort;
import com.pawcycle.backend.catalog.product.application.ProductSummary;
import java.math.BigDecimal;
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
class ProductDiscoveryPageFirstIntegrationTests {
  @Autowired private ProductDiscoveryQueryRepository repository;
  @Autowired private JdbcTemplate jdbc;

  private String suffix;
  private String parentSlug;
  private String childSlug;
  private String brandSlug;
  private String facetKey;
  private long parentId;
  private long categoryId;
  private long brandId;
  private long facetOptionId;
  private long emptyId;
  private long unavailableId;
  private long mixedId;
  private long ratedId;
  private long newestId;
  private long representativeSkuId;

  @BeforeEach
  void seed() {
    suffix = UUID.randomUUID().toString();
    parentSlug = "page-parent-" + suffix;
    childSlug = "page-child-" + suffix;
    brandSlug = "page-brand-" + suffix;
    facetKey = "page-facet-" + suffix;
    parentId = category(parentSlug, null, true);
    categoryId = category(childSlug, parentId, true);
    jdbc.update(
        "INSERT INTO brands(name,slug,logo_url,active,display_order) VALUES ('Page brand',?,'brand-logo',true,0)",
        brandSlug);
    brandId = lastId();
    jdbc.update("INSERT INTO facet_definitions(`key`,name) VALUES (?,'Protein')", facetKey);
    long facetId = lastId();
    jdbc.update(
        "INSERT INTO facet_options(facet_definition_id,value,display_order) VALUES (?,'salmon',0)",
        facetId);
    facetOptionId = lastId();

    emptyId = product("empty");
    unavailableId = product("unavailable");
    sku(unavailableId, "active-unavailable", 40, null, false, "ACTIVE", null);
    sku(unavailableId, "inactive-stock", 1, null, true, "INACTIVE", 10);
    mixedId = product("mixed");
    representativeSkuId = sku(mixedId, "representative", 30, 60, false, "ACTIVE", 0);
    sku(mixedId, "same-price-later", 30, 70, true, "ACTIVE", 3);
    sku(mixedId, "expensive", 90, null, false, "ACTIVE", 1);
    sku(mixedId, "inactive-cheapest", 1, null, true, "INACTIVE", 10);
    jdbc.update(
        "INSERT INTO product_images(product_id,image_url,display_order,image_type) VALUES (?,'main-image',0,'MAIN'),(?,'detail-image',1,'DETAIL')",
        mixedId, mixedId);
    review(mixedId, 5, true);
    review(mixedId, 3, true);
    review(mixedId, 1, false);
    ratedId = product("rated");
    sku(ratedId, "rated", 10, null, true, "ACTIVE", 2);
    review(ratedId, 5, true);
    newestId = product("newest");
    sku(newestId, "newest", 20, null, true, "ACTIVE", 2);
    review(newestId, 4, true);
    review(newestId, 4, true);
    review(newestId, 4, true);
  }

  @Test
  void newestFirstPageUsesDescendingProductIdsAndPreservesTotals() {
    assertPage(read(ProductSort.NEWEST, 0, 2), 0, 2, 5, newestId, ratedId);
  }

  @Test
  void newestNonZeroAndPastLastPagesUseDeterministicOffsets() {
    assertPage(read(ProductSort.NEWEST, 1, 2), 1, 2, 5, mixedId, unavailableId);
    assertPage(read(ProductSort.NEWEST, 2, 2), 2, 2, 5, emptyId);
    assertPage(read(ProductSort.NEWEST, 3, 2), 3, 2, 5);
  }

  @Test
  void recommendedMatchesNewestIncludingEveryProjectionAndPaginationField() {
    for (int page = 0; page <= 3; page++) {
      assertThat(read(ProductSort.RECOMMENDED, page, 2))
          .isEqualTo(read(ProductSort.NEWEST, page, 2));
    }
  }

  @Test
  void pageScopedProjectionPreservesSkuTieBreakAvailabilityImagesAndVisibleReviews() {
    for (ProductSort sort : List.of(ProductSort.NEWEST, ProductSort.RECOMMENDED)) {
      ProductSummary mixed = read(sort, 1, 2).items().getFirst();
      assertThat(mixed.productId()).isEqualTo(mixedId);
      assertThat(mixed.name()).isEqualTo("Page product mixed");
      assertThat(mixed.petType()).isEqualTo("DOG");
      assertThat(mixed.shortDescription()).isEqualTo("Page description");
      assertThat(mixed.thumbnailUrl()).isEqualTo("main-image");
      assertThat(mixed.category().categoryId()).isEqualTo(categoryId);
      assertThat(mixed.category().name()).isEqualTo("Page category");
      assertThat(mixed.category().slug()).isEqualTo(childSlug);
      assertThat(mixed.brand().brandId()).isEqualTo(brandId);
      assertThat(mixed.brand().name()).isEqualTo("Page brand");
      assertThat(mixed.brand().slug()).isEqualTo(brandSlug);
      assertThat(mixed.brand().logoUrl()).isEqualTo("brand-logo");
      assertThat(mixed.representativePrice()).isEqualByComparingTo("30");
      assertThat(mixed.compareAtPrice()).isEqualByComparingTo("60");
      assertThat(mixed.discountRate()).isEqualTo(50);
      assertThat(mixed.skuPriceSummary().skuPrices())
          .singleElement()
          .satisfies(sku -> {
            assertThat(sku.skuId()).isEqualTo(representativeSkuId);
            assertThat(sku.skuName()).isEqualTo("representative");
            assertThat(sku.price()).isEqualByComparingTo("30");
          });
      assertThat(mixed.hasSubscribableSku()).isTrue();
      assertThat(mixed.purchasable()).isTrue();
      assertThat(mixed.averageRating()).isEqualByComparingTo("4");
      assertThat(mixed.reviewCount()).isEqualTo(2);

      ProductSummary unavailable = read(sort, 1, 2).items().get(1);
      assertThat(unavailable.thumbnailUrl()).isEqualTo("fallback-image");
      assertThat(unavailable.hasSubscribableSku()).isFalse();
      assertThat(unavailable.purchasable()).isFalse();
      assertThat(unavailable.averageRating()).isNull();
      assertThat(unavailable.reviewCount()).isZero();
      ProductSummary empty = read(sort, 2, 2).items().getFirst();
      assertThat(empty.skuPriceSummary().skuPrices()).isEmpty();
      assertThat(empty.representativePrice()).isNull();
      assertThat(empty.compareAtPrice()).isNull();
      assertThat(empty.hasSubscribableSku()).isFalse();
      assertThat(empty.purchasable()).isFalse();
    }
  }

  @Test
  void dynamicFiltersApplyBeforePaginationIncludingParentCategoryAndSkuExists() {
    long firstMatch = product("filtered-first");
    sku(firstMatch, "match", 30, null, true, "ACTIVE", 1);
    long secondMatch = product("filtered-second");
    sku(secondMatch, "match", 30, null, true, "ACTIVE", 1);
    long splitPrice = product("split-price");
    sku(splitPrice, "too-low", 20, null, true, "ACTIVE", 1);
    sku(splitPrice, "too-high", 40, null, true, "ACTIVE", 1);
    long noSubscription = product("no-subscription");
    sku(noSubscription, "not-subscribable", 30, null, false, "ACTIVE", 1);
    long noStock = product("no-stock");
    sku(noStock, "no-stock", 30, null, true, "ACTIVE", 0);
    long noFacet = product("no-facet");
    sku(noFacet, "match", 30, null, true, "ACTIVE", 1);
    jdbc.update("DELETE FROM product_facet_values WHERE product_id=?", noFacet);
    long draftProduct = product("draft");
    sku(draftProduct, "match", 30, null, true, "ACTIVE", 1);
    jdbc.update("UPDATE products SET display_status='DRAFT' WHERE id=?", draftProduct);
    long otherPet = product("cat");
    sku(otherPet, "match", 30, null, true, "ACTIVE", 1);
    jdbc.update("UPDATE products SET pet_type='CAT' WHERE id=?", otherPet);

    for (ProductSort sort : List.of(ProductSort.NEWEST, ProductSort.RECOMMENDED)) {
      assertPage(filtered(sort, 0), 0, 1, 3, secondMatch);
      assertPage(filtered(sort, 1), 1, 1, 3, firstMatch);
      assertPage(filtered(sort, 2), 2, 1, 3, mixedId);
      ProductListView unavailable =
          repository.read(
              null, null, parentSlug, null, brandSlug, List.of(), null, null, false, false, 0, 2, sort);
      assertPage(unavailable, 0, 2, 2, unavailableId, emptyId);
      ProductListView expensiveSkuMatch =
          repository.read(
              null, null, parentSlug, null, brandSlug, List.of(), new BigDecimal("80"),
              new BigDecimal("100"), true, true, 0, 1, sort);
      assertPage(expensiveSkuMatch, 0, 1, 1, mixedId);
      assertThat(expensiveSkuMatch.items().getFirst().representativePrice())
          .isEqualByComparingTo("30");
    }
  }

  @Test
  void inactiveCategoryAndBrandAreExcludedBeforeSelectingThePage() {
    long inactiveCategory = category("inactive-" + suffix, parentId, false);
    long hiddenCategoryProduct = product("inactive-category");
    jdbc.update(
        "UPDATE products SET category_id=? WHERE id=?", inactiveCategory, hiddenCategoryProduct);
    jdbc.update(
        "INSERT INTO brands(name,slug,active,display_order) VALUES ('Inactive',?,false,0)",
        "inactive-" + suffix);
    long inactiveBrand = lastId();
    long hiddenBrandProduct = product("inactive-brand");
    jdbc.update("UPDATE products SET brand_id=? WHERE id=?", inactiveBrand, hiddenBrandProduct);
    ProductListView result = repository.read(null, null, parentSlug, 0, 2, ProductSort.NEWEST);
    assertPage(result, 0, 2, 5, newestId, ratedId);
  }

  @Test
  void calculatedSortsStillSortAllEligibleProductsBeforePaginating() {
    assertPage(read(ProductSort.PRICE_ASC, 0, 2), 0, 2, 5, ratedId, newestId);
    assertPage(read(ProductSort.PRICE_ASC, 1, 2), 1, 2, 5, mixedId, unavailableId);
    assertPage(read(ProductSort.PRICE_DESC, 0, 2), 0, 2, 5, unavailableId, mixedId);
    assertPage(read(ProductSort.PRICE_DESC, 1, 2), 1, 2, 5, newestId, ratedId);
    assertPage(read(ProductSort.RATING, 0, 2), 0, 2, 5, ratedId, newestId);
    assertPage(read(ProductSort.RATING, 1, 2), 1, 2, 5, mixedId, unavailableId);
    assertPage(read(ProductSort.REVIEW_COUNT, 0, 2), 0, 2, 5, newestId, mixedId);
    assertPage(read(ProductSort.REVIEW_COUNT, 1, 2), 1, 2, 5, ratedId, unavailableId);
    for (ProductSort sort : List.of(
        ProductSort.PRICE_ASC, ProductSort.PRICE_DESC, ProductSort.RATING, ProductSort.REVIEW_COUNT)) {
      assertPage(read(sort, 2, 2), 2, 2, 5, emptyId);
      assertThat(read(sort, 0, 5).items())
          .containsExactlyInAnyOrderElementsOf(read(ProductSort.NEWEST, 0, 5).items());
    }
  }

  private ProductListView read(ProductSort sort, int page, int size) {
    return repository.read(
        null, null, null, null, brandSlug, List.of(), null, null, null, null, page, size, sort);
  }

  private ProductListView filtered(ProductSort sort, int page) {
    return repository.read(
        " PAGE DESCRIPTION ", " dog ", parentSlug.toUpperCase(java.util.Locale.ROOT),
        childSlug.toUpperCase(java.util.Locale.ROOT), brandSlug.toUpperCase(java.util.Locale.ROOT),
        List.of(facetKey + ":salmon"), new BigDecimal("25"), new BigDecimal("35"), true, true, page, 1, sort);
  }

  private static void assertPage(ProductListView result, int page, int size, long total, Long... ids) {
    assertThat(result.items()).extracting(ProductSummary::productId).containsExactly(ids);
    assertThat(result.page()).isEqualTo(page);
    assertThat(result.size()).isEqualTo(size);
    assertThat(result.totalElements()).isEqualTo(total);
    assertThat(result.totalPages()).isEqualTo((int) Math.ceil((double) total / size));
  }

  private long category(String slug, Long parent, boolean active) {
    jdbc.update(
        "INSERT INTO categories(name,slug,display_order,active,parent_id) VALUES ('Page category',?,0,?,?)",
        slug, active, parent);
    return lastId();
  }

  private long product(String key) {
    jdbc.update(
        """
        INSERT INTO products(brand_id,catalog_key,category_id,name,short_description,description,pet_type,thumbnail_url,display_status)
        VALUES (?,?,?,?,'Page description','Page full description','DOG','fallback-image','PUBLIC')
        """,
        brandId, key + "-" + suffix, categoryId, "Page product " + key);
    long id = lastId();
    jdbc.update(
        "INSERT INTO product_facet_values(product_id,facet_option_id) VALUES (?,?)", id, facetOptionId);
    return id;
  }

  private long sku(
      long product,
      String name,
      int price,
      Integer compareAt,
      boolean subscribable,
      String status,
      Integer stock) {
    jdbc.update(
        "INSERT INTO skus(product_id,sku_code,name,price,compare_at_price,subscribable,display_order,status) VALUES (?,?,?,?,?,?,0,?)",
        product, UUID.randomUUID().toString(), name, price, compareAt, subscribable, status);
    long id = lastId();
    if (stock != null)
      jdbc.update("INSERT INTO inventories(sku_id,available_quantity) VALUES (?,?)", id, stock);
    return id;
  }

  private void review(long product, int rating, boolean visible) {
    jdbc.update(
        "INSERT INTO members(email,password_hash) VALUES (?,'test-only')",
        UUID.randomUUID() + "@example.test");
    long member = lastId();
    jdbc.update(
        "INSERT INTO reviews(product_id,member_id,rating,content,visible,created_at,updated_at) VALUES (?,?,?,'Page review',?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
        product, member, rating, visible);
  }

  private long lastId() {
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }
}
