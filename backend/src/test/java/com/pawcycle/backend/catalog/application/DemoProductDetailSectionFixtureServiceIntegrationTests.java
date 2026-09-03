package com.pawcycle.backend.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.foundation.bootstrap.LocalCommerceDemoFixtureService;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoProductDetailSectionFixtureServiceIntegrationTests {

  private final WebApplicationContext applicationContext;
  private final JdbcTemplate jdbc;
  private final DemoCatalogManifestImportService importService;
  private final DemoProductDetailSectionFixtureService detailFixtureService;
  private MockMvc mockMvc;

  @Autowired
  DemoProductDetailSectionFixtureServiceIntegrationTests(
      WebApplicationContext applicationContext,
      JdbcTemplate jdbc,
      NativeQueryExecutor jdbcExecutor,
      DemoCatalogManifestImportService importService) {
    this.applicationContext = applicationContext;
    this.jdbc = jdbc;
    this.importService = importService;
    this.detailFixtureService = new DemoProductDetailSectionFixtureService(jdbcExecutor);
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
  }

  @Test
  void fixtureAddsThreeVisibleSectionsPerV1ProductAndIsIdempotent() throws Exception {
    new LocalCommerceDemoFixtureService(importService, detailFixtureService).bootstrap();
    new LocalCommerceDemoFixtureService(importService, detailFixtureService).bootstrap();

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT product.catalog_key, section.display_order, section.visible, section.title, section.body
            FROM product_detail_sections section
            JOIN products product ON product.id=section.product_id
            WHERE product.catalog_key LIKE 'demo-%'
            ORDER BY product.catalog_key, section.display_order
            """);
    assertThat(rows).hasSize(96);
    Map<String, List<Integer>> ordersByProduct = new HashMap<>();
    for (Map<String, Object> row : rows) {
      assertThat(row.get("visible")).isIn(true, 1);
      assertThat(row.get("title")).isNotEqualTo("");
      assertThat(row.get("body").toString()).isNotBlank().doesNotContain("<", ">");
      ordersByProduct
          .computeIfAbsent((String) row.get("catalog_key"), ignored -> new java.util.ArrayList<>())
          .add(((Number) row.get("display_order")).intValue());
    }
    assertThat(ordersByProduct).hasSize(32);
    assertThat(ordersByProduct.values())
        .allSatisfy(orders -> assertThat(orders).containsExactly(10, 20, 30));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_detail_sections section JOIN products product ON"
                    + " product.id=section.product_id WHERE product.catalog_key LIKE 'demo-%'",
                Integer.class))
        .isEqualTo(96);

    Long productId =
        jdbc.queryForObject(
            "SELECT id FROM products WHERE catalog_key=?", Long.class, "demo-dog-food-salmon");
    mockMvc
        .perform(get("/api/products/{productId}", productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.detailSections.length()").value(3))
        .andExpect(jsonPath("$.detailSections[0].displayOrder").value(10))
        .andExpect(jsonPath("$.detailSections[1].displayOrder").value(20))
        .andExpect(jsonPath("$.detailSections[2].displayOrder").value(30))
        .andExpect(jsonPath("$.detailSections[0].body").isNotEmpty());
  }

  @Test
  void missingCatalogKeyFailsFastWithFixtureError() throws Exception {
    Path fixture = Files.createTempFile("missing-detail-section-", ".json");
    try {
      Files.writeString(
          fixture,
          """
          {
            "version": 1,
            "sections": [
              {"catalogKey":"demo-not-present","title":"상품 특징","body":"데모 내용","displayOrder":10,"visible":true},
              {"catalogKey":"demo-not-present","title":"사용 방법","body":"데모 내용","displayOrder":20,"visible":true}
            ]
          }
          """,
          StandardCharsets.UTF_8);
      ReflectionTestUtils.setField(
          detailFixtureService, "fixtureLocation", fixture.toUri().toString());

      assertThatThrownBy(detailFixtureService::bootstrap)
          .isInstanceOf(CatalogManifestImportException.class)
          .hasMessageContaining("demo-not-present");
    } finally {
      Files.deleteIfExists(fixture);
    }
  }
}
