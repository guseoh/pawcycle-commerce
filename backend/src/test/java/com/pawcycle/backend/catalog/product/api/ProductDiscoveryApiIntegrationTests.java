package com.pawcycle.backend.catalog.product.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.maintenance.persistence.DemoCatalogImportPersistence;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.foundation.bootstrap.LocalCommerceDemoFixtureService;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductDiscoveryApiIntegrationTests {
  private final WebApplicationContext applicationContext;
  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final EntityManager entityManager;
  private final LocalCommerceDemoFixtureService fixtureService;
  private final JdbcTemplate jdbcTemplate;
  private MockMvc mockMvc;

  @Autowired
  ProductDiscoveryApiIntegrationTests(
      WebApplicationContext applicationContext,
      CategoryRepository categoryRepository,
      ProductRepository productRepository,
      EntityManager entityManager,
      DemoCatalogImportPersistence importService,
      JdbcTemplate jdbcTemplate) {
    this.applicationContext = applicationContext;
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.entityManager = entityManager;
    this.fixtureService = new LocalCommerceDemoFixtureService(importService);
    this.jdbcTemplate = jdbcTemplate;
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
  }

  @Test
  void categoryFieldsAndDiscoveryFiltersAreCaseInsensitive() throws Exception {
    Category category =
        categoryRepository.saveAndFlush(
            new Category(
                "Dog Food " + UUID.randomUUID(), "dog-food-" + UUID.randomUUID(), 0, true));
    Product product =
        productRepository.save(
            new Product(category, "Dog Food", "Daily DOG food", null, "DOG", null, "PUBLIC"));
    entityManager.flush();
    entityManager.clear();

    mockMvc
        .perform(
            get("/api/products")
                .param("q", "dOg FoOd")
                .param("petType", "dog")
                .param("category", category.getSlug().toUpperCase(java.util.Locale.ROOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].productId").value(product.getId()))
        .andExpect(jsonPath("$.items[0].category.categoryId").value(category.getId()))
        .andExpect(jsonPath("$.items[0].category.name").value(category.getName()))
        .andExpect(jsonPath("$.items[0].category.slug").value(category.getSlug()));

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortDescription").value(product.getShortDescription()))
        .andExpect(jsonPath("$.detailSections").isArray())
        .andExpect(jsonPath("$.detailSections").isEmpty())
        .andExpect(jsonPath("$.trust.averageRating").doesNotExist())
        .andExpect(jsonPath("$.trust.reviewCount").value(0))
        .andExpect(jsonPath("$.trust.questionCount").value(0))
        .andExpect(jsonPath("$.category.categoryId").value(category.getId()))
        .andExpect(jsonPath("$.category.name").value(category.getName()))
        .andExpect(jsonPath("$.category.slug").value(category.getSlug()));
  }

  @Test
  void searchKeywordWildcardsMatchLiterally() throws Exception {
    Category category =
        categoryRepository.saveAndFlush(
            new Category("Treat " + UUID.randomUUID(), "treat-" + UUID.randomUUID(), 0, true));
    LiteralSearchFixture fixture = literalSearchFixture(category);

    assertSearchKeywordsMatchLiterally(category, fixture);
    assertSearchMatches(category, "l\\s", fixture.backslash());
    assertSearchMatches(category, "\\", fixture.backslash());
  }

  @Test
  void searchKeywordWildcardsMatchLiterallyWithoutBackslashEscapes() throws Exception {
    Category category =
        categoryRepository.saveAndFlush(
            new Category("Treat " + UUID.randomUUID(), "treat-" + UUID.randomUUID(), 0, true));
    LiteralSearchFixture fixture = literalSearchFixture(category);
    String originalSqlMode = jdbcTemplate.queryForObject("SELECT @@SESSION.sql_mode", String.class);
    try {
      // The test transaction binds one connection, so the API request below runs in this mode.
      jdbcTemplate.execute(
          "SET SESSION sql_mode = CONCAT_WS(',', NULLIF(@@SESSION.sql_mode, ''), 'NO_BACKSLASH_ESCAPES')");
      assertThat(jdbcTemplate.queryForObject("SELECT @@SESSION.sql_mode", String.class))
          .contains("NO_BACKSLASH_ESCAPES");

      // Backslash keywords are covered only in the default mode: the JDBC driver does not follow
      // this mid-session sql_mode switch and still quotes a bound backslash as two characters.
      // That would test the driver's quoting, not the LIKE escape.
      assertSearchKeywordsMatchLiterally(category, fixture);
    } finally {
      jdbcTemplate.update("SET SESSION sql_mode = ?", originalSqlMode);
    }
  }

  private record LiteralSearchFixture(
      Product percent,
      Product thousand,
      Product underscore,
      Product backslash,
      Product exclamation) {}

  private LiteralSearchFixture literalSearchFixture(Category category) {
    Product percent =
        productRepository.save(
            new Product(category, "100% Natural Treat", "single protein", null, "DOG", null, "PUBLIC"));
    Product thousand =
        productRepository.save(
            new Product(category, "1000 Natural Treat", "mixed", null, "DOG", null, "PUBLIC"));
    Product underscore =
        productRepository.save(
            new Product(category, "chew_bone mini", "dental", null, "DOG", null, "PUBLIC"));
    productRepository.save(
        new Product(category, "chewXbone mini", "dental", null, "DOG", null, "PUBLIC"));
    Product backslash =
        productRepository.save(
            new Product(category, "bowl\\stand set", "feeder", null, "DOG", null, "PUBLIC"));
    productRepository.save(
        new Product(category, "bowlXstand set", "feeder", null, "DOG", null, "PUBLIC"));
    Product exclamation =
        productRepository.save(
            new Product(category, "Wow! Chew", "toy", null, "DOG", null, "PUBLIC"));
    productRepository.save(
        new Product(category, "Wow Chew", "toy", null, "DOG", null, "PUBLIC"));
    entityManager.flush();
    entityManager.clear();
    return new LiteralSearchFixture(percent, thousand, underscore, backslash, exclamation);
  }

  private void assertSearchKeywordsMatchLiterally(Category category, LiteralSearchFixture fixture)
      throws Exception {
    assertSearchMatches(category, "100%", fixture.percent());
    assertSearchMatches(category, "100% NATURAL", fixture.percent());
    assertSearchMatches(category, "chew_bone", fixture.underscore());
    assertSearchMatches(category, "%", fixture.percent());
    assertSearchMatches(category, "_", fixture.underscore());
    assertSearchMatches(category, "wow!", fixture.exclamation());
    assertSearchMatches(category, "!", fixture.exclamation());
    // Plain keywords keep case-insensitive partial matching.
    assertSearchMatches(category, "nAtUrAl tReAt", fixture.thousand(), fixture.percent());
  }

  private void assertSearchMatches(Category category, String keyword, Product... expected)
      throws Exception {
    // JsonPath reads small numeric ids as Integer.
    Object[] expectedIds =
        Arrays.stream(expected).map(product -> Math.toIntExact(product.getId())).toArray();
    mockMvc
        .perform(get("/api/products").param("q", keyword).param("category", category.getSlug()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(expected.length))
        .andExpect(jsonPath("$.items[*].productId", containsInAnyOrder(expectedIds)));
  }

  @Test
  void demoCatalogSupportsPaginationSearchSortAndOutOfStockDetail() throws Exception {
    fixtureService.bootstrap();

    mockMvc
        .perform(
            get("/api/products")
                .param("petType", "DOG")
                .param("category", "food")
                .param("page", "1")
                .param("size", "2")
                .param("sort", "PRICE_ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.items[0].petType").value("DOG"))
        .andExpect(jsonPath("$.items[0].category.slug").value("food"));

    mockMvc
        .perform(
            get("/api/products").param("q", "퍼피").param("petType", "DOG").param("category", "food"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("퍼피 스타터 사료"));

    mockMvc
        .perform(
            get("/api/products")
                .param("petType", "DOG")
                .param("category", "food")
                .param("size", "100")
                .param("sort", "PRICE_ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].representativePrice").value(15900.00))
        .andExpect(jsonPath("$.items[4].representativePrice").value(22900.00));

    mockMvc
        .perform(
            get("/api/products")
                .param("petType", "DOG")
                .param("category", "food")
                .param("size", "100")
                .param("sort", "PRICE_DESC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].representativePrice").value(22900.00))
        .andExpect(jsonPath("$.items[4].representativePrice").value(15900.00));

    Long outOfStockProductId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM products WHERE catalog_key=?", Long.class, "demo-dog-treats-training");
    mockMvc
        .perform(get("/api/products/{productId}", outOfStockProductId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skus.length()").value(1))
        .andExpect(jsonPath("$.skus[0].availableQuantity").value(0))
        .andExpect(jsonPath("$.skus[0].purchasable").value(false))
        .andExpect(jsonPath("$.purchasable").value(false));
  }
}
