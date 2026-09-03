package com.pawcycle.backend.catalog.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import java.util.ArrayList;
import java.util.List;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogDiscoveryApiIntegrationTests {
  private final WebApplicationContext applicationContext;
  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private MockMvc mockMvc;

  @Autowired
  CatalogDiscoveryApiIntegrationTests(
      WebApplicationContext applicationContext,
      CategoryRepository categoryRepository,
      ProductRepository productRepository,
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper) {
    this.applicationContext = applicationContext;
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
  }

  @Test
  void publicDiscoveryExposesActiveHierarchyBrandsAndCategoryFacets() throws Exception {
    Fixture fixture = seedFixture();

    JsonNode root =
        objectMapper.readTree(
            mockMvc
                .perform(get("/api/catalog/discovery"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    JsonNode top = findByField(root.path("categories"), "slug", fixture.topSlug());
    JsonNode otherTop = findByField(root.path("categories"), "slug", fixture.otherTopSlug());
    assertThat(top).isNotNull();
    assertThat(otherTop).isNotNull();
    assertThat(findByField(root.path("categories"), "slug", fixture.grandchildSlug())).isNull();
    assertThat(findByField(root.path("categories"), "slug", fixture.inactiveTopSlug())).isNull();
    assertThat(findByField(root.path("categories"), "slug", fixture.orphanSlug())).isNull();
    assertThat(findByField(root.path("categories"), "slug", "__pawcycle_uncategorized__")).isNull();
    assertThat(testSlugs(root.path("categories"), fixture.topSlug(), fixture.otherTopSlug()))
        .containsExactly(fixture.otherTopSlug(), fixture.topSlug());

    JsonNode children = top.path("children");
    assertThat(children.size()).isEqualTo(2);
    assertThat(testSlugs(children, fixture.childFirstSlug(), fixture.childSecondSlug()))
        .containsExactly(fixture.childSecondSlug(), fixture.childFirstSlug());
    JsonNode child = findByField(children, "slug", fixture.childSecondSlug());
    assertThat(child).isNotNull();
    assertThat(child.has("children")).isFalse();

    List<String> brandSlugs =
        testSlugs(root.path("brands"), fixture.brandOneSlug(), fixture.brandTwoSlug());
    assertThat(brandSlugs).containsExactly(fixture.brandTwoSlug(), fixture.brandOneSlug());
    assertThat(findByField(root.path("brands"), "slug", fixture.inactiveBrandSlug())).isNull();

    JsonNode childFacets =
        findByField(root.path("categoryFacets"), "categorySlug", fixture.childSecondSlug());
    assertThat(childFacets).isNotNull();
    assertThat(facetKeys(childFacets.path("facets")))
        .containsExactly(fixture.textureKey(), fixture.proteinKey());
    JsonNode protein = findByField(childFacets.path("facets"), "key", fixture.proteinKey());
    assertThat(protein).isNotNull();
    assertThat(
            testFieldValues(
                protein.path("options"),
                "value",
                fixture.proteinEarlyValue(),
                fixture.proteinLateValue()))
        .containsExactly(fixture.proteinEarlyValue(), fixture.proteinLateValue());
    assertThat(facetKeys(childFacets.path("facets"))).doesNotContain(fixture.otherFacetKey());
    assertThat(findByField(root.path("categoryFacets"), "categorySlug", fixture.grandchildSlug()))
        .isNull();
    assertThat(findByField(root.path("categoryFacets"), "categorySlug", fixture.inactiveTopSlug()))
        .isNull();
    assertThat(findByField(root.path("categoryFacets"), "categorySlug", fixture.orphanSlug()))
        .isNull();
  }

  @Test
  void existingCategoryAndProductContractsRemainUnchanged() throws Exception {
    Fixture fixture = seedFixture();
    Category category = categoryRepository.findById(fixture.topCategoryId()).orElseThrow();
    Product product =
        productRepository.saveAndFlush(
            new Product(
                category, "Discovery contract product", "상품 설명", null, "DOG", null, "PUBLIC"));

    JsonNode categories =
        objectMapper.readTree(
            mockMvc
                .perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(categories.path("items").isArray()).isTrue();
    assertThat(findByField(categories.path("items"), "slug", fixture.topSlug())).isNotNull();
    for (JsonNode item : categories.path("items")) assertThat(item.has("children")).isFalse();

    JsonNode products =
        objectMapper.readTree(
            mockMvc
                .perform(get("/api/products").param("category", fixture.topSlug()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(findByField(products.path("items"), "productId", product.getId().toString()))
        .isNotNull();

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(
                        objectMapper
                            .readTree(result.getResponse().getContentAsString())
                            .path("category")
                            .path("slug")
                            .asText())
                    .isEqualTo(fixture.topSlug()));
  }

  private Fixture seedFixture() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String topSlug = "discovery-top-" + suffix;
    long topId = insertCategory("탐색 상위", topSlug, 2, true, null);
    String childFirstSlug = "discovery-child-first-" + suffix;
    insertCategory("탐색 하위 첫째", childFirstSlug, 3, true, topId);
    String childSecondSlug = "discovery-child-second-" + suffix;
    long childSecondId = insertCategory("탐색 하위 둘째", childSecondSlug, 1, true, topId);
    String grandchildSlug = "discovery-grandchild-" + suffix;
    long grandchildId = insertCategory("탐색 손자", grandchildSlug, 0, true, childSecondId);

    String otherTopSlug = "discovery-other-top-" + suffix;
    long otherTopId = insertCategory("탐색 다른 상위", otherTopSlug, 1, true, null);
    String inactiveTopSlug = "discovery-inactive-top-" + suffix;
    long inactiveTopId = insertCategory("비활성 상위", inactiveTopSlug, 0, false, null);
    String inactiveChildSlug = "discovery-inactive-child-" + suffix;
    insertCategory("비활성 하위", inactiveChildSlug, 0, false, topId);
    String orphanSlug = "discovery-orphan-" + suffix;
    long orphanId = insertCategory("비활성 상위의 활성 하위", orphanSlug, 0, true, inactiveTopId);

    String brandOneSlug = "discovery-brand-one-" + suffix;
    insertBrand("탐색 브랜드 하나", brandOneSlug, true, 5);
    String brandTwoSlug = "discovery-brand-two-" + suffix;
    insertBrand("탐색 브랜드 둘", brandTwoSlug, true, 2);
    String inactiveBrandSlug = "discovery-brand-inactive-" + suffix;
    insertBrand("비활성 탐색 브랜드", inactiveBrandSlug, false, 0);

    String proteinKey = "protein-" + suffix;
    long proteinId = insertFacet(proteinKey, "주원료");
    String proteinLateValue = "연어-늦음-" + suffix;
    insertOption(proteinId, proteinLateValue, 2);
    String proteinEarlyValue = "연어-빠름-" + suffix;
    insertOption(proteinId, proteinEarlyValue, 0);
    assignFacet(childSecondId, proteinId, 1);

    String textureKey = "texture-" + suffix;
    long textureId = insertFacet(textureKey, "식감");
    insertOption(textureId, "바삭-" + suffix, 0);
    assignFacet(childSecondId, textureId, 0);

    String otherFacetKey = "other-facet-" + suffix;
    long otherFacetId = insertFacet(otherFacetKey, "다른 카테고리용");
    insertOption(otherFacetId, "다른 값-" + suffix, 0);
    assignFacet(otherTopId, otherFacetId, 0);
    assignFacet(grandchildId, otherFacetId, 1);
    assignFacet(inactiveTopId, otherFacetId, 1);
    assignFacet(orphanId, otherFacetId, 1);

    return new Fixture(
        topId,
        topSlug,
        childFirstSlug,
        childSecondSlug,
        grandchildSlug,
        otherTopSlug,
        inactiveTopSlug,
        orphanSlug,
        brandOneSlug,
        brandTwoSlug,
        inactiveBrandSlug,
        proteinKey,
        textureKey,
        otherFacetKey,
        proteinEarlyValue,
        proteinLateValue);
  }

  private long insertCategory(
      String name, String slug, int displayOrder, boolean active, Long parentId) {
    jdbcTemplate.update(
        "INSERT INTO categories(name,slug,display_order,active,parent_id) VALUES (?,?,?,?,?)",
        name,
        slug,
        displayOrder,
        active,
        parentId);
    return jdbcTemplate.queryForObject("SELECT id FROM categories WHERE slug=?", Long.class, slug);
  }

  private void insertBrand(String name, String slug, boolean active, int displayOrder) {
    jdbcTemplate.update(
        "INSERT INTO brands(name,slug,logo_url,active,display_order) VALUES (?,?,NULL,?,?)",
        name,
        slug,
        active,
        displayOrder);
  }

  private long insertFacet(String key, String name) {
    jdbcTemplate.update("INSERT INTO facet_definitions(`key`,name) VALUES (?,?)", key, name);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM facet_definitions WHERE `key`=?", Long.class, key);
  }

  private void insertOption(long facetId, String value, int displayOrder) {
    jdbcTemplate.update(
        "INSERT INTO facet_options(facet_definition_id,value,display_order) VALUES (?,?,?)",
        facetId,
        value,
        displayOrder);
  }

  private void assignFacet(long categoryId, long facetId, int displayOrder) {
    jdbcTemplate.update(
        "INSERT INTO category_facets(category_id,facet_definition_id,display_order) VALUES (?,?,?)",
        categoryId,
        facetId,
        displayOrder);
  }

  private JsonNode findByField(JsonNode array, String field, String value) {
    for (JsonNode node : array) if (value.equals(node.path(field).asText())) return node;
    return null;
  }

  private List<String> testSlugs(JsonNode array, String... expected) {
    return testFieldValues(array, "slug", expected);
  }

  private List<String> testFieldValues(JsonNode array, String field, String... expected) {
    List<String> candidates = List.of(expected);
    List<String> result = new ArrayList<>();
    for (JsonNode node : array)
      if (candidates.contains(node.path(field).asText())) result.add(node.path(field).asText());
    return result;
  }

  private List<String> facetKeys(JsonNode array) {
    List<String> keys = new ArrayList<>();
    for (JsonNode node : array) keys.add(node.path("key").asText());
    return keys;
  }

  private record Fixture(
      long topCategoryId,
      String topSlug,
      String childFirstSlug,
      String childSecondSlug,
      String grandchildSlug,
      String otherTopSlug,
      String inactiveTopSlug,
      String orphanSlug,
      String brandOneSlug,
      String brandTwoSlug,
      String inactiveBrandSlug,
      String proteinKey,
      String textureKey,
      String otherFacetKey,
      String proteinEarlyValue,
      String proteinLateValue) {}
}
