package com.pawcycle.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.recommendation.application.RecommendationAiClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "pawcycle.recommendation.ai.enabled=false")
@ActiveProfiles("test")
@Transactional
class RecommendationInteractionErrorContractIntegrationTests {
  @Autowired WebApplicationContext context;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @MockitoBean RecommendationAiClient ai;
  private MockMvc mvc;
  private long memberId, petId, productId;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    String key = "error-contract-" + UUID.randomUUID();
    jdbc.update("INSERT INTO members(email,password_hash,role) VALUES (?,'fixture','USER')", key + "@example.test");
    memberId = lastId();
    jdbc.update("INSERT INTO pets(member_id,name,pet_type) VALUES (?,'fixture','DOG')", memberId);
    petId = lastId();
    jdbc.update("INSERT INTO categories(name,slug,display_order,active) VALUES ('fixture',?,0,true)", key);
    long categoryId = lastId();
    jdbc.update("INSERT INTO brands(name,slug,display_order,active) VALUES ('fixture',?,0,true)", key);
    long brandId = lastId();
    jdbc.update("INSERT INTO products(category_id,brand_id,name,short_description,pet_type,display_status) VALUES (?,?,'fixture','fixture','DOG','PUBLIC')", categoryId, brandId);
    productId = lastId();
    jdbc.update("INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES (?,?,'fixture',100,true,0,'ACTIVE')", productId, key);
    jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,10,0,0)", lastId());
    when(ai.recommend(anyList(), anyList())).thenReturn(List.of());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/api/recommendations/products", "/api/recommendations/products?petId=",
      "/api/recommendations/products?petId=not-a-long",
      "/api/recommendations/popular?limit=abc", "/api/recommendations/trending?limit=abc",
      "/api/products/not-a-long/related", "/api/products/not-a-long/complementary",
      "/api/products/1/related?limit=abc", "/api/products/1/complementary?limit=abc"
  })
  void invalidRecommendationBindingReturnsValidation400(String path) throws Exception {
    frameworkError(mvc.perform(get(path).with(authenticated())));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{", "", "{\"events\":{}}", "{\"events\":\"invalid\"}"})
  void malformedInteractionBindingReturnsValidation400(String body) throws Exception {
    frameworkError(events(body));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interaction_events WHERE member_id=?", Integer.class, memberId)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"events\":[]}", "{\"events\":[null]}"})
  void semanticInteractionValidationKeepsTwoField400(String body) throws Exception {
    twoFieldError(events(body), 400, "VALIDATION_FAILED");
  }

  @Test
  void interactionNotFoundResponsesKeepTheirCodesAndTwoFieldShape() throws Exception {
    twoFieldError(events(event(productId, Long.MAX_VALUE)), 404, "PET_NOT_FOUND");
    twoFieldError(events(event(Long.MAX_VALUE, petId)), 404, "PRODUCT_NOT_FOUND");
  }

  @Test
  void recommendationDomainErrorsKeepThreeFieldResponses() throws Exception {
    for (String path : List.of("/api/recommendations/popular?limit=11",
        "/api/recommendations/trending?limit=0", "/api/products/1/related?limit=0",
        "/api/products/1/complementary?limit=7")) {
      mvc.perform(get(path)).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }
    mvc.perform(get("/api/recommendations/products").param("petId", Long.toString(Long.MAX_VALUE)).with(authenticated()))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PET_NOT_FOUND"))
        .andExpect(jsonPath("$.fieldErrors").isEmpty());
  }

  @Test
  void securityFiltersStillRunBeforeMalformedInputHandling() throws Exception {
    mvc.perform(get("/api/recommendations/products")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/interactions").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/interactions").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isForbidden());
  }

  @Test
  void successfulPersonalizedFallbackAndInteraction204ArePreserved() throws Exception {
    mvc.perform(get("/api/recommendations/products").param("petId", Long.toString(petId)).with(authenticated()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.requestId").isString())
        .andExpect(jsonPath("$.products[0].productId").value(productId))
        .andExpect(jsonPath("$.products[0].strategy").value("PERSONALIZED"));
    mvc.perform(get("/api/recommendations/popular")).andExpect(status().isOk())
        .andExpect(jsonPath("$.products[0].productId").value(productId));
    var result = events(event(productId, petId)).andExpect(status().isNoContent()).andReturn();
    assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interaction_events WHERE member_id=?", Integer.class, memberId)).isEqualTo(1);
  }

  private void frameworkError(ResultActions result) throws Exception {
    var response = result.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.fieldErrors").isArray()).andReturn();
    assertThat(json.readTree(response.getResponse().getContentAsByteArray()).size()).isEqualTo(3);
  }

  private void twoFieldError(ResultActions result, int statusCode, String code) throws Exception {
    var response = result.andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.fieldErrors").doesNotExist()).andReturn();
    assertThat(json.readTree(response.getResponse().getContentAsByteArray()).size()).isEqualTo(2);
  }

  private ResultActions events(String body) throws Exception {
    return mvc.perform(post("/api/interactions").with(authenticated()).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String event(long product, long pet) {
    return "{\"events\":[{\"eventId\":\"%s\",\"type\":\"PRODUCT_VIEW\",\"productId\":%d,\"petId\":%d}]}"
        .formatted(UUID.randomUUID(), product, pet);
  }

  private RequestPostProcessor authenticated() {
    return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(memberId), null,
        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  private long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
}
