package com.pawcycle.backend.catalog.product.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.maintenance.persistence.DemoCatalogImportPersistence;
import com.pawcycle.backend.foundation.bootstrap.LocalCommerceDemoFixtureService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class ProductDiscoveryPaginationRegressionIntegrationTests {
  private final WebApplicationContext applicationContext;
  private final LocalCommerceDemoFixtureService fixtureService;
  private final ObjectMapper objectMapper;
  private MockMvc mockMvc;

  @Autowired
  ProductDiscoveryPaginationRegressionIntegrationTests(
      WebApplicationContext applicationContext,
      DemoCatalogImportPersistence importService,
      ObjectMapper objectMapper) {
    this.applicationContext = applicationContext;
    this.fixtureService = new LocalCommerceDemoFixtureService(importService);
    this.objectMapper = objectMapper;
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
  }

  @Test
  void adjacentPagesUseOffsetWithoutRepeatingProductIds() throws Exception {
    fixtureService.bootstrap();

    JsonNode page0 = page(0);
    JsonNode page1 = page(1);
    List<Long> page0Ids = ids(page0.path("items"));
    List<Long> page1Ids = ids(page1.path("items"));

    assertThat(page0.path("page").asInt()).isZero();
    assertThat(page1.path("page").asInt()).isEqualTo(1);
    assertThat(page0Ids).hasSize(2);
    assertThat(page1Ids).hasSize(2);
    assertThat(page0Ids).doesNotContainAnyElementsOf(page1Ids);
    assertThat(page0.path("items").get(0).path("representativePrice").decimalValue())
        .isLessThanOrEqualTo(page0.path("items").get(1).path("representativePrice").decimalValue());
    assertThat(page1.path("items").get(0).path("representativePrice").decimalValue())
        .isLessThanOrEqualTo(page1.path("items").get(1).path("representativePrice").decimalValue());
  }

  private JsonNode page(int page) throws Exception {
    String body =
        mockMvc
            .perform(
                get("/api/products")
                    .param("petType", "DOG")
                    .param("category", "food")
                    .param("page", Integer.toString(page))
                    .param("size", "2")
                    .param("sort", "PRICE_ASC"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private static List<Long> ids(JsonNode items) {
    List<Long> ids = new ArrayList<>();
    items.forEach(item -> ids.add(item.path("productId").asLong()));
    return ids;
  }
}
