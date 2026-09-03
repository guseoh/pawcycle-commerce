package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.admin.application.CatalogExpansionAdminService;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService;
import com.pawcycle.backend.catalog.application.DemoProductDetailSectionFixtureService;
import com.pawcycle.backend.catalog.engagement.application.ProductEngagementService;
import com.pawcycle.backend.catalog.engagement.application.ReviewCreateCommand;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(LocalCustomerCatalogV3FixtureIntegrationTests.FixtureConfiguration.class)
@Transactional
class LocalCustomerCatalogV3FixtureIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired LocalCustomerCatalogV3FixtureService fixture;
  @Autowired DemoCatalogManifestImportService baseline;
  @Autowired ProductEngagementService engagement;
  @Autowired WebApplicationContext context;
  @Autowired ObjectMapper mapper;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void preservesV1AndCreatesDeterministicIdempotentSupplement() {
    baseline.apply();
    Map<String, Object> v1 = baselineSnapshot();
    fixture.bootstrap();
    Map<String, Object> first = catalogSnapshot();
    fixture.bootstrap();

    assertThat(catalogSnapshot()).isEqualTo(first);
    assertThat(baselineSnapshot()).isEqualTo(v1);
    assertThat(baseline.validate().productsCreated()).isZero();
    assertThat(count("SELECT COUNT(*) FROM products WHERE catalog_key LIKE 'demo-%'"))
        .isEqualTo(32);
    assertThat(count("SELECT COUNT(*) FROM skus WHERE sku_code LIKE 'DEMO-%'")).isEqualTo(42);
    assertThat(count("SELECT COUNT(*) FROM subscription_plans WHERE plan_key LIKE 'demo-%'"))
        .isEqualTo(6);
    assertThat(count("SELECT COUNT(*) FROM products")).isEqualTo(100);
    assertThat(count("SELECT COUNT(*) FROM skus")).isEqualTo(166);
    assertThat(count("SELECT COUNT(*) FROM brands")).isEqualTo(10);
    assertThat(count("SELECT COUNT(*) FROM categories")).isEqualTo(28);
    assertThat(count("SELECT COUNT(*) FROM categories WHERE slug<>'__pawcycle_uncategorized__'"))
        .isEqualTo(27);
    assertThat(
            jdbc.queryForList(
                "SELECT COUNT(*) FROM products GROUP BY pet_type ORDER BY pet_type", Integer.class))
        .containsExactly(50, 50);
    assertThat(
            jdbc.queryForList(
                "SELECT COUNT(*) FROM products WHERE catalog_key LIKE 'qa3-%' GROUP BY brand_id",
                Integer.class))
        .hasSize(9)
        .allMatch(n -> n >= 4);
    assertThat(count("SELECT COUNT(*) FROM categories WHERE parent_id IS NOT NULL")).isEqualTo(18);
    assertThat(
            count(
                "SELECT COUNT(*) FROM categories c JOIN categories parent ON parent.id=c.parent_id"
                    + " WHERE parent.parent_id IS NOT NULL"))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM reviews")).isZero();
    assertThat(count("SELECT COUNT(*) FROM product_questions")).isZero();
  }

  @Test
  void enforcesImageOptionFacetPriceAndInventoryCoverage() {
    fixture.bootstrap();
    assertThat(
            count(
                "SELECT COUNT(*) FROM products p WHERE p.catalog_key LIKE 'qa3-%' AND (SELECT"
                    + " COUNT(*) FROM product_images i WHERE i.product_id=p.id AND"
                    + " i.image_type='MAIN')<>1"))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM skus WHERE compare_at_price IS NOT NULL AND"
                    + " compare_at_price<=price"))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM skus WHERE compare_at_price IS NOT NULL")).isPositive();
    assertThat(count("SELECT COUNT(*) FROM skus WHERE compare_at_price IS NULL")).isPositive();
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT (SELECT COUNT(*) FROM product_option_groups g WHERE"
                    + " g.product_id=p.id) FROM products p WHERE p.catalog_key LIKE 'qa3-%'",
                Integer.class))
        .containsExactlyInAnyOrder(0, 1, 2);
    assertThat(
            count(
                "SELECT COUNT(*) FROM (SELECT v.sku_id,g.id FROM sku_option_values v JOIN"
                    + " product_option_values ov ON ov.id=v.option_value_id JOIN"
                    + " product_option_groups g ON g.id=ov.option_group_id GROUP BY v.sku_id,g.id"
                    + " HAVING COUNT(*)>1) duplicates"))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM sku_option_values v JOIN skus s ON s.id=v.sku_id JOIN"
                    + " product_option_values ov ON ov.id=v.option_value_id JOIN"
                    + " product_option_groups g ON g.id=ov.option_group_id WHERE"
                    + " g.product_id<>s.product_id"))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM (SELECT product_id,combination FROM (SELECT"
                    + " s.id,s.product_id,COALESCE(GROUP_CONCAT(v.option_value_id ORDER BY"
                    + " v.option_value_id),'') combination FROM skus s LEFT JOIN sku_option_values"
                    + " v ON v.sku_id=s.id WHERE s.sku_code LIKE 'QA3-%' GROUP BY"
                    + " s.id,s.product_id) combinations GROUP BY product_id,combination HAVING"
                    + " COUNT(*)>1) duplicates"))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM product_facet_values v JOIN products p ON p.id=v.product_id"
                    + " JOIN facet_options o ON o.id=v.facet_option_id LEFT JOIN category_facets cf"
                    + " ON cf.category_id=p.category_id AND"
                    + " cf.facet_definition_id=o.facet_definition_id WHERE cf.category_id IS NULL"))
        .isZero();
    for (String condition :
        List.of(
            "i.available_quantity=0",
            "i.available_quantity BETWEEN 1 AND 5",
            "i.available_quantity>5",
            "s.subscribable=true",
            "s.subscribable=false")) {
      assertThat(
              count(
                  "SELECT COUNT(*) FROM skus s JOIN inventories i ON i.sku_id=s.id WHERE s.sku_code"
                      + " LIKE 'QA3-%' AND "
                      + condition))
          .isPositive();
    }
    assertThat(
            count(
                "SELECT COUNT(*) FROM products p WHERE p.catalog_key LIKE 'qa3-%' AND (SELECT"
                    + " COUNT(*) FROM product_detail_sections d WHERE d.product_id=p.id AND"
                    + " d.visible=true)=3"))
        .isEqualTo(68);
    assertThat(
            count(
                "SELECT COUNT(DISTINCT body) FROM product_detail_sections d JOIN products p ON"
                    + " p.id=d.product_id WHERE p.catalog_key LIKE 'qa3-%'"))
        .isGreaterThan(180);
  }

  @Test
  void repeatedImportPreservesMutableInventoryAndRejectsConflictingCatalog() {
    fixture.bootstrap();
    long skuId =
        jdbc.queryForObject(
            "SELECT id FROM skus WHERE sku_code='QA3-DOG-SALMON-SMALL-1'", Long.class);
    jdbc.update(
        "UPDATE inventories SET available_quantity=17,reserved_quantity=2,version=7 WHERE sku_id=?",
        skuId);
    fixture.bootstrap();
    assertThat(
            jdbc.queryForMap(
                "SELECT available_quantity,reserved_quantity,version FROM inventories WHERE"
                    + " sku_id=?",
                skuId))
        .containsEntry("available_quantity", 17)
        .containsEntry("reserved_quantity", 2)
        .containsEntry("version", 7L);
    jdbc.update("UPDATE products SET name='사용자가 수정한 상품' WHERE catalog_key='qa3-dog-salmon-small'");
    assertThatThrownBy(fixture::bootstrap)
        .isInstanceOf(LocalQaBootstrapException.class)
        .hasMessageContaining("products name");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void lateConflictRollsBackBaselineAndSupplementTogether() {
    int productsBefore = count("SELECT COUNT(*) FROM products");
    jdbc.update(
        "INSERT INTO brands(name,slug,active,display_order) VALUES ('충돌 브랜드','wear-petal',true,9)");
    try {
      assertThatThrownBy(fixture::bootstrap)
          .isInstanceOf(LocalQaBootstrapException.class)
          .hasMessageContaining("brands name");
      assertThat(count("SELECT COUNT(*) FROM products")).isEqualTo(productsBefore);
      assertThat(count("SELECT COUNT(*) FROM brands WHERE slug='grain-tail'")).isZero();
    } finally {
      jdbc.update("DELETE FROM brands WHERE slug='wear-petal'");
    }
  }

  @Test
  void publicListFiltersAndSortsUseV3Data() throws Exception {
    fixture.bootstrap();
    assertThat(list("petType", "DOG").get("totalElements").asInt()).isEqualTo(50);
    assertThat(list("petType", "CAT").get("totalElements").asInt()).isEqualTo(50);
    assertThat(list("category", "food").get("totalElements").asInt()).isEqualTo(22);
    JsonNode dry = list("category", "food", "subcategory", "food-dry", "brand", "grain-tail");
    assertThat(dry.get("totalElements").asInt()).isEqualTo(8);
    JsonNode salmon =
        list(
            "petType",
            "DOG",
            "category",
            "food",
            "subcategory",
            "food-dry",
            "brand",
            "grain-tail",
            "facet",
            "protein:연어",
            "minPrice",
            "8900",
            "maxPrice",
            "8900",
            "subscribable",
            "true",
            "purchasable",
            "true");
    assertThat(salmon.get("totalElements").asInt()).isEqualTo(1);
    assertThat(salmon.at("/items/0/productId").asLong())
        .isEqualTo(productId("qa3-dog-salmon-small"));
    // The salmon SKUs straddle this interval, but none costs 9000..9800.
    // Both bounds must match the same ACTIVE SKU, not separate EXISTS rows.
    assertThat(
            list("minPrice", "9000", "maxPrice", "9800", "brand", "grain-tail")
                .get("totalElements")
                .asInt())
        .isEqualTo(1);
    for (boolean flag : List.of(true, false)) {
      JsonNode purchasable = list("purchasable", String.valueOf(flag));
      assertThat(purchasable.get("totalElements").asInt()).isPositive();
      purchasable
          .get("items")
          .forEach(item -> assertThat(item.get("purchasable").asBoolean()).isEqualTo(flag));
      JsonNode subscribable = list("subscribable", String.valueOf(flag));
      assertThat(subscribable.get("totalElements").asInt()).isPositive();
      subscribable
          .get("items")
          .forEach(item -> assertThat(item.get("hasSubscribableSku").asBoolean()).isEqualTo(flag));
    }
    List<BigDecimal> ascending = prices(list("sort", "PRICE_ASC"));
    List<BigDecimal> descending = prices(list("sort", "PRICE_DESC"));
    assertThat(ascending).isSorted();
    assertThat(descending).isSortedAccordingTo(Comparator.reverseOrder());
    assertThat(ascending.getFirst()).isLessThan(descending.getFirst());
    assertThat(list().get("items")).isEqualTo(list("sort", "NEWEST").get("items"));
    assertThat(list().get("items")).isEqualTo(list("sort", "RECOMMENDED").get("items"));
    assertThat(list("sort", "RATING").get("items")).isEqualTo(list().get("items"));
    assertThat(list("sort", "REVIEW_COUNT").get("items")).isEqualTo(list().get("items"));
  }

  @Test
  void publicDetailExposesImagesOptionsDiscountSectionsAndEmptyTrust() throws Exception {
    fixture.bootstrap();
    JsonNode detail = detail("qa3-dog-salmon-small");
    assertThat(detail.at("/brand/slug").asText()).isEqualTo("grain-tail");
    assertThat(detail.get("images").size()).isEqualTo(4);
    assertThat(detail.at("/images/0/imageType").asText()).isEqualTo("MAIN");
    assertThat(detail.get("optionGroups").size()).isEqualTo(2);
    assertThat(detail.at("/skus/0/selectedOptions").size()).isEqualTo(2);
    assertThat(detail.at("/skus/0/selectedOptions/0/value").asText()).isEqualTo("1kg");
    assertThat(detail.at("/skus/0/compareAtPrice").decimalValue()).isEqualByComparingTo("12900");
    assertThat(detail.at("/skus/0/discountRate").asInt()).isEqualTo(31);
    assertThat(detail.at("/skus/0/purchasable").asBoolean()).isFalse();
    assertThat(detail.at("/skus/1/purchasable").asBoolean()).isTrue();
    assertThat(detail.get("purchasable").asBoolean()).isTrue();
    assertThat(detail.get("detailSections").size()).isEqualTo(3);
    assertThat(detail.at("/trust/reviewCount").asInt()).isZero();
    assertThat(detail.at("/trust/averageRating").isNull()).isTrue();
    assertThat(detail.at("/trust/questionCount").asInt()).isZero();
    assertThat(detail("qa3-cat-cotton-kicker").get("optionGroups").isEmpty()).isTrue();
    assertThat(detail("qa3-cat-tuna-pate").get("optionGroups").size()).isEqualTo(1);
    assertThat(detail("qa3-cat-chicken-soup").get("purchasable").asBoolean()).isFalse();
    JsonNode v1 = detail("demo-dog-food-salmon");
    assertThat(v1.get("images").isEmpty()).isTrue();
    assertThat(v1.get("thumbnailUrl").asText()).startsWith("https://images.unsplash.com/");
    assertThat(v1.get("detailSections").size()).isBetween(2, 4);
  }

  @Test
  void ratingAndReviewCountSortsRespectQualifiedReviewsOnV3Products() throws Exception {
    fixture.bootstrap();
    long few = productId("qa3-dog-salmon-small");
    long many = productId("qa3-cat-indoor-salmon");
    // Test-only delivered order setup follows ProductEngagementApiIntegrationTests.
    // The runnable catalog fixture never fabricates purchases or members.
    qualifiedReview(few, "qa3-review-one@example.test", 5);
    qualifiedReview(many, "qa3-review-two@example.test", 3);
    qualifiedReview(many, "qa3-review-three@example.test", 4);
    assertThat(list("sort", "RATING").at("/items/0/productId").asLong()).isEqualTo(few);
    assertThat(list("sort", "REVIEW_COUNT").at("/items/0/productId").asLong()).isEqualTo(many);
    JsonNode summary = detail("qa3-cat-indoor-salmon").get("trust");
    assertThat(summary.get("reviewCount").asInt()).isEqualTo(2);
    assertThat(summary.get("averageRating").decimalValue()).isEqualByComparingTo("3.5");
  }

  private void qualifiedReview(long productId, String email, int rating) {
    jdbc.update(
        "INSERT INTO members(email,password_hash,role) VALUES (?,'non-login-test-fixture','USER')",
        email);
    long member = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    Map<String, Object> sku =
        jdbc.queryForList(
                "SELECT id,sku_code,name,price FROM skus WHERE product_id=? ORDER BY id", productId)
            .getFirst();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO"
            + " orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,created_at)"
            + " VALUES (?,?,'ONE_TIME','PAID',?,0,0,?,?)",
        email,
        member,
        sku.get("price"),
        sku.get("price"),
        now);
    long order = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO"
            + " order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount)"
            + " VALUES (?,?,'FULL',?,?,?,?,1,?)",
        order,
        sku.get("id"),
        sku.get("sku_code"),
        "V3 테스트 상품",
        sku.get("name"),
        sku.get("price"),
        sku.get("price"));
    jdbc.update(
        "INSERT INTO deliveries(order_id,status,shipped_at,delivered_at) VALUES"
            + " (?,'DELIVERED',?,?)",
        order,
        now,
        now);
    engagement.createReview(
        productId, member, new ReviewCreateCommand(rating, "옵션 구성을 확인한 테스트 리뷰"));
  }

  private JsonNode list(String... parameters) throws Exception {
    var request = get("/api/products").param("size", "100");
    for (int i = 0; i < parameters.length; i += 2) request.param(parameters[i], parameters[i + 1]);
    return mapper.readTree(
        mvc.perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode detail(String key) throws Exception {
    return mapper.readTree(
        mvc.perform(get("/api/products/{id}", productId(key)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private long productId(String key) {
    return jdbc.queryForObject("SELECT id FROM products WHERE catalog_key=?", Long.class, key);
  }

  private int count(String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }

  private List<BigDecimal> prices(JsonNode page) {
    List<BigDecimal> result = new ArrayList<>();
    page.get("items").forEach(item -> result.add(item.get("representativePrice").decimalValue()));
    return result;
  }

  private Map<String, Object> baselineSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put(
        "products",
        jdbc.queryForList("SELECT * FROM products WHERE catalog_key LIKE 'demo-%' ORDER BY id"));
    snapshot.put(
        "skus", jdbc.queryForList("SELECT * FROM skus WHERE sku_code LIKE 'DEMO-%' ORDER BY id"));
    for (String table :
        List.of(
            "subscription_plans", "plan_versions", "plan_items", "plan_version_delivery_cycles"))
      snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table));
    return snapshot;
  }

  private Map<String, Object> catalogSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    for (String table :
        List.of(
            "brands",
            "categories",
            "products",
            "skus",
            "inventories",
            "product_images",
            "product_option_groups",
            "product_option_values",
            "sku_option_values",
            "facet_definitions",
            "facet_options",
            "category_facets",
            "product_facet_values",
            "product_detail_sections"))
      snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1,2"));
    return snapshot;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixtureConfiguration {
    @Bean
    LocalCustomerCatalogV3FixtureService customerCatalogV3UnderTest(
        NativeQueryExecutor jdbcExecutor,
        DemoCatalogManifestImportService importer,
        CatalogExpansionAdminService expansion,
        ProductListCacheInvalidator cache,
        Validator validator) {
      var detail =
          new DemoProductDetailSectionFixtureService(
              jdbcExecutor, "classpath:catalog/demo-product-detail-sections.json");
      return new LocalCustomerCatalogV3FixtureService(
          jdbcExecutor,
          new LocalCommerceDemoFixtureService(importer, detail),
          expansion,
          cache,
          validator);
    }
  }
}
