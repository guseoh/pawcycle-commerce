package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class CatalogExpansionReviewRegressionIntegrationTests {
  private final WebApplicationContext applicationContext;
  private final ObjectMapper objectMapper;
  private final JdbcTemplate jdbc;
  private MockMvc mockMvc;

  @Autowired
  CatalogExpansionReviewRegressionIntegrationTests(
      WebApplicationContext applicationContext, ObjectMapper objectMapper, JdbcTemplate jdbc) {
    this.applicationContext = applicationContext;
    this.objectMapper = objectMapper;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
    jdbc.update(
        "INSERT IGNORE INTO members(id,email,password_hash,role) VALUES"
            + " (1,'catalog-review-fixture@example.test','fixture','ADMIN')");
    cleanCatalog();
  }

  @AfterEach
  void tearDown() {
    cleanCatalog();
  }

  @Test
  void patchDistinguishesOmittedFieldsFromExplicitNull() throws Exception {
    long brandId =
        json(postJson(
                "/api/admin/brands",
                """
                {"name":"리뷰 브랜드","slug":"review-brand","logoUrl":"https://example.test/logo.png","active":true,"displayOrder":1}
                """))
            .get("brandId")
            .asLong();

    patchJson("/api/admin/brands/" + brandId, "{\"name\":\"수정 브랜드\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("수정 브랜드"))
        .andExpect(jsonPath("$.logoUrl").value("https://example.test/logo.png"));

    patchJson("/api/admin/brands/" + brandId, "{\"logoUrl\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logoUrl").value(nullValue()));

    patchJson("/api/admin/brands/" + brandId, "{\"name\":null}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));

    long productId = createProduct();
    long imageId =
        json(postJson(
                "/api/admin/products/" + productId + "/images",
                """
                {"imageUrl":"https://example.test/main.png","altText":"기존 대체 텍스트","displayOrder":0,"imageType":"MAIN"}
                """))
            .get("imageId")
            .asLong();

    patchJson("/api/admin/products/" + productId + "/images/" + imageId, "{\"altText\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.altText").value(nullValue()));

    patchJson("/api/admin/products/" + productId + "/images/" + imageId, "{\"imageUrl\":null}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("imageUrl"));
  }

  @Test
  void invalidCompareAtPriceReportsCompareAtPriceField() throws Exception {
    long productId = createProduct();
    long skuId =
        json(postJson(
                "/api/admin/products/" + productId + "/skus",
                """
                {"skuCode":"REVIEW-PRICE-SKU","name":"기본","price":10000.00,"subscribable":true,"displayOrder":0,"status":"ACTIVE"}
                """))
            .get("skuId")
            .asLong();

    patchJson("/api/admin/products/" + productId + "/skus/" + skuId, "{\"compareAtPrice\":-1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("compareAtPrice"));
  }

  @Test
  void concurrentSkuOptionAssignmentsCannotCreateDuplicateCombination() throws Exception {
    long productId = createProduct();
    long firstSkuId = createSku(productId, "REVIEW-OPTION-1");
    long secondSkuId = createSku(productId, "REVIEW-OPTION-2");
    long groupId =
        json(postJson(
                "/api/admin/products/" + productId + "/option-groups",
                """
                {"name":"용량","displayOrder":0}
                """))
            .get("optionGroupId")
            .asLong();
    long valueId =
        json(postJson(
                "/api/admin/products/" + productId + "/option-groups/" + groupId + "/values",
                """
                {"value":"2kg","displayOrder":0}
                """))
            .get("optionValueId")
            .asLong();
    String body = "{\"optionValueIds\":[%d]}".formatted(valueId);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    List<MvcResult> results = new ArrayList<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<MvcResult>> futures =
          List.of(
              executor.submit(
                  () ->
                      executeTogether(
                          () ->
                              putJson(
                                  "/api/admin/products/"
                                      + productId
                                      + "/skus/"
                                      + firstSkuId
                                      + "/option-values",
                                  body),
                          ready,
                          start)),
              executor.submit(
                  () ->
                      executeTogether(
                          () ->
                              putJson(
                                  "/api/admin/products/"
                                      + productId
                                      + "/skus/"
                                      + secondSkuId
                                      + "/option-values",
                                  body),
                          ready,
                          start)));
      ready.await();
      start.countDown();
      for (Future<MvcResult> future : futures) results.add(future.get());
    }

    assertThat(results)
        .extracting(result -> result.getResponse().getStatus())
        .containsExactlyInAnyOrder(200, 409);
    MvcResult conflict =
        results.stream()
            .filter(result -> result.getResponse().getStatus() == 409)
            .findFirst()
            .orElseThrow();
    assertThat(json(conflict).get("code").asText()).isEqualTo("SKU_OPTION_COMBINATION_CONFLICT");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM sku_option_values WHERE option_value_id=?",
                Long.class,
                valueId))
        .isEqualTo(1L);
  }

  private long createProduct() throws Exception {
    long categoryId =
        json(postJson(
                "/api/admin/categories",
                """
                {"name":"리뷰 카테고리","slug":"review-category-%d","displayOrder":0,"active":true}
                """
                    .formatted(System.nanoTime())))
            .get("categoryId")
            .asLong();
    return json(postJson(
            "/api/admin/products",
            """
            {"categoryId":%d,"brandId":1,"name":"리뷰 상품","shortDescription":"리뷰 회귀","description":null,"petType":"DOG","thumbnailUrl":null}
            """
                .formatted(categoryId)))
        .get("productId")
        .asLong();
  }

  private long createSku(long productId, String skuCode) throws Exception {
    return json(postJson(
            "/api/admin/products/" + productId + "/skus",
            """
            {"skuCode":"%s","name":"옵션 SKU","price":10000.00,"subscribable":true,"displayOrder":0,"status":"ACTIVE"}
            """
                .formatted(skuCode)))
        .get("skuId")
        .asLong();
  }

  private MvcResult executeTogether(Request request, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    start.await();
    return request.perform();
  }

  private MvcResult postJson(String path, String body) throws Exception {
    return performJson(post(path), body).andExpect(status().is2xxSuccessful()).andReturn();
  }

  private MvcResult putJson(String path, String body) throws Exception {
    return performJson(put(path), body).andReturn();
  }

  private org.springframework.test.web.servlet.ResultActions patchJson(String path, String body)
      throws Exception {
    return performJson(patch(path), body);
  }

  private org.springframework.test.web.servlet.ResultActions performJson(
      MockHttpServletRequestBuilder request, String body) throws Exception {
    return mockMvc.perform(
        request
            .with(
                authentication(
                    new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMemberPrincipal(1L, MemberRole.ADMIN),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private void cleanCatalog() {
    jdbc.update("DELETE FROM product_facet_values");
    jdbc.update("DELETE FROM category_facets");
    jdbc.update("DELETE FROM facet_options");
    jdbc.update("DELETE FROM facet_definitions");
    jdbc.update("DELETE FROM sku_option_values");
    jdbc.update("DELETE FROM product_option_values");
    jdbc.update("DELETE FROM product_option_groups");
    jdbc.update("DELETE FROM product_images");
    jdbc.update("DELETE FROM inventory_movements");
    jdbc.update("DELETE FROM inventories");
    jdbc.update("DELETE FROM skus");
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM categories");
    jdbc.update("DELETE FROM brands WHERE id<>1");
  }

  @FunctionalInterface
  private interface Request {
    MvcResult perform() throws Exception;
  }
}
