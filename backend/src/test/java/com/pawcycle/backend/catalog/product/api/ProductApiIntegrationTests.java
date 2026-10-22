package com.pawcycle.backend.catalog.product.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.application.ProductDetailUnavailableException;
import com.pawcycle.backend.catalog.product.application.ProductListUnavailableException;
import com.pawcycle.backend.catalog.product.application.ProductQueryService;
import com.pawcycle.backend.catalog.product.application.ProductSort;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class ProductApiIntegrationTests {

  private final WebApplicationContext applicationContext;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final SkuRepository skuRepository;
  private final EntityManager entityManager;
  private final Statistics statistics;
  private MockMvc mockMvc;
  @MockitoSpyBean private ProductQueryService productQueryService;

  @Autowired
  ProductApiIntegrationTests(
      WebApplicationContext applicationContext,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      SkuRepository skuRepository,
      EntityManager entityManager,
      EntityManagerFactory entityManagerFactory) {
    this.applicationContext = applicationContext;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.skuRepository = skuRepository;
    this.entityManager = entityManager;
    this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
  }

  @Test
  void anonymousListMatchesPaginatedShapeAndNewestOrder() throws Exception {
    Product first = saveProduct("첫 상품", "DOG", null, null, "PUBLIC");
    Product second = saveProduct("둘째 상품", "CAT", "상세", "https://example.test/cat.png", "PUBLIC");
    saveSku(first, "동률 뒤 SKU", "39000.00", false, 2);
    saveSku(first, "첫 SKU", "19000.00", true, 1);
    saveSku(first, "동률 앞 SKU", "29000.00", false, 2);
    flushAndResetStatistics();

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.items[0].productId").value(second.getId()))
        .andExpect(jsonPath("$.items[0].petType").value("CAT"))
        .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://example.test/cat.png"))
        .andExpect(jsonPath("$.items[0].skuPriceSummary.skuPrices").isEmpty())
        .andExpect(jsonPath("$.items[0].representativePrice").value(nullValue()))
        .andExpect(jsonPath("$.items[0].purchasable").value(false))
        .andExpect(jsonPath("$.items[1].productId").value(first.getId()))
        .andExpect(jsonPath("$.items[1].category.categoryId").isNumber())
        .andExpect(jsonPath("$.items[1].category.name").isString())
        .andExpect(jsonPath("$.items[1].category.slug").isString())
        .andExpect(jsonPath("$.items[1].thumbnailUrl").value(nullValue()))
        .andExpect(jsonPath("$.items[1].skuPriceSummary.skuPrices.length()").value(1))
        .andExpect(jsonPath("$.items[1].skuPriceSummary.skuPrices[0].skuName").value("첫 SKU"))
        .andExpect(jsonPath("$.items[1].skuPriceSummary.skuPrices[0].price").value(19000.00))
        .andExpect(jsonPath("$.items[1].representativePrice").value(19000.00))
        .andExpect(jsonPath("$.items[1].hasSubscribableSku").value(true))
        .andExpect(jsonPath("$.items[1].purchasable").value(false))
        .andExpect(jsonPath("$.items[0].displayStatus").doesNotExist());
  }

  @Test
  void anonymousEmptyListReturnsEmptyPage() throws Exception {
    flushAndResetStatistics();

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));
  }

  @Test
  void authenticatedDetailNeedsNoCsrfAndReturnsInventoryProjection() throws Exception {
    Product product = saveProduct("상세 상품", "DOG", null, null, "PUBLIC");
    saveSku(product, "구독 SKU", "19900.00", true, 1);
    saveSku(product, "일반 SKU", "29900.00", false, 2);
    flushAndResetStatistics();

    mockMvc
        .perform(
            get("/api/products/{productId}", product.getId())
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(
                            new AuthenticatedMemberPrincipal(1L), null, List.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productId").value(product.getId()))
        .andExpect(jsonPath("$.description").value(nullValue()))
        .andExpect(jsonPath("$.thumbnailUrl").value(nullValue()))
        .andExpect(jsonPath("$.category.categoryId").isNumber())
        .andExpect(jsonPath("$.skus[0].subscribable").value(true))
        .andExpect(jsonPath("$.skus[0].availableDeliveryCycles.length()").value(3))
        .andExpect(jsonPath("$.skus[0].availableDeliveryCycles[0]").value(2))
        .andExpect(jsonPath("$.skus[0].availableDeliveryCycles[1]").value(4))
        .andExpect(jsonPath("$.skus[0].availableDeliveryCycles[2]").value(8))
        .andExpect(jsonPath("$.skus[0].availableQuantity").value(0))
        .andExpect(jsonPath("$.skus[0].purchasable").value(false))
        .andExpect(jsonPath("$.skus[1].subscribable").value(false))
        .andExpect(jsonPath("$.skus[1].availableDeliveryCycles").isEmpty())
        .andExpect(jsonPath("$.skus[1].availableQuantity").value(0))
        .andExpect(jsonPath("$.skus[1].purchasable").value(false))
        .andExpect(jsonPath("$.purchasable").value(false))
        .andExpect(jsonPath("$.displayStatus").doesNotExist());
  }

  @Test
  void anonymousDetailAndAuthenticatedListArePublicWithoutCsrf() throws Exception {
    Product product = saveProduct("공개 접근 상품", "CAT", "상세", null, "PUBLIC");
    flushAndResetStatistics();

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skus").isEmpty());

    mockMvc
        .perform(
            get("/api/products")
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(
                            new AuthenticatedMemberPrincipal(1L), null, List.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].productId").value(product.getId()));
  }

  @Test
  void onlyPublicProductsAreExposed() throws Exception {
    Product visible = saveProduct("공개", "DOG", null, null, "PUBLIC");
    Product draft = saveProduct("초안", "DOG", null, null, "DRAFT");
    Product inactive = saveProduct("비활성", "DOG", null, null, "INACTIVE");
    String suffix = UUID.randomUUID().toString();
    Category inactiveCategory =
        categoryRepository.saveAndFlush(new Category("숨김 " + suffix, "hidden-" + suffix, 0, false));
    Product publicProductInInactiveCategory =
        productRepository.save(
            new Product(
                inactiveCategory,
                "숨김 카테고리 공개 상품",
                "공개이지만 카테고리가 비활성입니다.",
                null,
                "DOG",
                null,
                "PUBLIC"));
    flushAndResetStatistics();

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].productId").value(visible.getId()));

    for (Long hiddenId :
        List.of(draft.getId(), inactive.getId(), publicProductInInactiveCategory.getId())) {
      mockMvc
          .perform(get("/api/products/{productId}", hiddenId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
          .andExpect(jsonPath("$.message").value("상품을 확인할 수 없습니다."))
          .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }
  }

  @Test
  void missingAndNonPublicDetailsReturnIdenticalErrors() throws Exception {
    Product hidden = saveProduct("비공개", "CAT", null, null, "INACTIVE");
    flushAndResetStatistics();

    MvcResult hiddenResult =
        mockMvc
            .perform(get("/api/products/{productId}", hidden.getId()))
            .andExpect(status().isNotFound())
            .andReturn();
    MvcResult missingResult =
        mockMvc
            .perform(get("/api/products/{productId}", Long.MAX_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

    assertThat(hiddenResult.getResponse().getContentAsString())
        .isEqualTo(missingResult.getResponse().getContentAsString());
  }

  @Test
  void publicApisExposeOnlyActiveSkusAndKeepProductWhenNoActiveSkuExists() throws Exception {
    Product product = saveProduct("SKU 상태 상품", "DOG", null, null, "PUBLIC");
    skuRepository.save(
        new Sku(
            product,
            "ACTIVE-" + product.getId(),
            "활성 SKU",
            new BigDecimal("1000.00"),
            true,
            1,
            SkuStatus.ACTIVE));
    skuRepository.save(
        new Sku(
            product,
            "INACTIVE-" + product.getId(),
            "비활성 SKU",
            new BigDecimal("2000.00"),
            true,
            2,
            SkuStatus.INACTIVE));
    flushAndResetStatistics();

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].skuPriceSummary.skuPrices.length()").value(1))
        .andExpect(jsonPath("$.items[0].skuPriceSummary.skuPrices[0].skuName").value("활성 SKU"));

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skus.length()").value(1))
        .andExpect(jsonPath("$.skus[0].skuName").value("활성 SKU"));

    skuRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId()).stream()
        .filter(sku -> sku.getStatus() == SkuStatus.ACTIVE)
        .forEach(sku -> sku.update(null, null, null, null, SkuStatus.INACTIVE));
    entityManager.flush();
    entityManager.clear();

    mockMvc
        .perform(get("/api/products/{productId}", product.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skus").isEmpty());

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].productId").value(product.getId()))
        .andExpect(jsonPath("$.items[0].skuPriceSummary.skuPrices").isEmpty())
        .andExpect(jsonPath("$.items[0].hasSubscribableSku").value(false));
  }

  @Test
  void listFiltersByQueryPetTypeAndCategorySlug() throws Exception {
    Product dogFood = saveProduct("강아지 사료", "DOG", null, null, "PUBLIC");
    saveProduct("고양이 모래", "CAT", null, null, "PUBLIC");
    flushAndResetStatistics();

    mockMvc
        .perform(
            get("/api/products")
                .param("q", "사료")
                .param("petType", "dog")
                .param("category", dogFood.getCategory().getSlug()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].productId").value(dogFood.getId()));

    mockMvc
        .perform(get("/api/products").param("q", "모래").param("petType", "DOG"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "/api/products, sort, invalid",
    "/api/products, page, invalid",
    "/api/products, size, invalid",
    "/api/products, minPrice, invalid",
    "/api/products, maxPrice, invalid",
    "/api/products, subscribable, invalid",
    "/api/products, purchasable, invalid",
    "/api/products/compare, productId, invalid"
  })
  void malformedPublicParametersUseCommonValidationContract(
      String path, String parameter, String value) throws Exception {
    mockMvc
        .perform(get(path).param(parameter, value))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("""
            {
              "code": "VALIDATION_FAILED",
              "message": "요청 값을 확인해 주세요.",
              "fieldErrors": [
                {"field": "%s", "message": "요청 값의 형식이 올바르지 않습니다."}
              ]
            }
            """.formatted(parameter), JsonCompareMode.STRICT));
  }

  @Test
  void missingComparisonParameterUsesCommonValidationContract() throws Exception {
    mockMvc
        .perform(get("/api/products/compare"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("""
            {
              "code": "VALIDATION_FAILED",
              "message": "요청 값을 확인해 주세요.",
              "fieldErrors": [
                {"field": "productId", "message": "요청 파라미터가 필요합니다."}
              ]
            }
            """, JsonCompareMode.STRICT));
  }

  @Test
  void nonNumericDetailIdKeepsProductNotFoundContract() throws Exception {
    mockMvc
        .perform(get("/api/products/not-a-number"))
        .andExpect(status().isNotFound())
        .andExpect(content().json("""
            {
              "code": "PRODUCT_NOT_FOUND",
              "message": "상품을 확인할 수 없습니다.",
              "fieldErrors": []
            }
            """, JsonCompareMode.STRICT));
  }

  @Test
  void comparisonDomainErrorsKeepTheirMessagesAndStatuses() throws Exception {
    mockMvc
        .perform(get("/api/products/compare").param("productId", "1", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().json("""
            {
              "code": "VALIDATION_FAILED",
              "message": "상품은 서로 다른 2~3개를 선택해야 합니다.",
              "fieldErrors": []
            }
            """, JsonCompareMode.STRICT));
    mockMvc
        .perform(get("/api/products/compare")
            .param("productId", Long.toString(Long.MAX_VALUE), Long.toString(Long.MAX_VALUE - 1)))
        .andExpect(status().isNotFound())
        .andExpect(content().json("""
            {
              "code": "PRODUCT_NOT_FOUND",
              "message": "상품을 확인할 수 없습니다.",
              "fieldErrors": []
            }
            """, JsonCompareMode.STRICT));
  }

  @ParameterizedTest
  @CsvSource({
    "page, -1, 'page는 0 이상, size는 1~100이어야 합니다.'",
    "size, 0, 'page는 0 이상, size는 1~100이어야 합니다.'",
    "minPrice, -1, 가격 범위가 올바르지 않습니다.",
    "facet, broken, facet은 key:value 형식이어야 합니다."
  })
  void domainValidationKeepsExistingMessage(
      String parameter, String value, String message) throws Exception {
    mockMvc
        .perform(get("/api/products").param(parameter, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value(message))
        .andExpect(jsonPath("$.fieldErrors").isEmpty());
  }

  @Test
  void unexpectedListFailureKeepsSafe500WithAllAdviceActive() throws Exception {
    doThrow(new ProductListUnavailableException(new IllegalStateException("products table")))
        .when(productQueryService).findProducts(null, null, null, 0, 20, ProductSort.NEWEST);

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().json("""
            {
              "code": "PRODUCT_LIST_UNAVAILABLE",
              "message": "상품 목록을 불러오지 못했습니다.",
              "fieldErrors": []
            }
            """, JsonCompareMode.STRICT));
  }

  @Test
  void unexpectedDetailFailureKeepsSafe500WithAllAdviceActive() throws Exception {
    doThrow(new ProductDetailUnavailableException(new IllegalStateException("price column")))
        .when(productQueryService).findProduct(Long.MAX_VALUE);

    mockMvc
        .perform(get("/api/products/{productId}", Long.MAX_VALUE))
        .andExpect(status().isInternalServerError())
        .andExpect(content().json("""
            {
              "code": "PRODUCT_DETAIL_UNAVAILABLE",
              "message": "상품 정보를 불러오지 못했습니다.",
              "fieldErrors": []
            }
            """, JsonCompareMode.STRICT));
  }

  private Product saveProduct(
      String name, String petType, String description, String thumbnailUrl, String displayStatus) {
    String suffix = UUID.randomUUID().toString();
    Category category =
        categoryRepository.saveAndFlush(
            new Category("product-api-" + suffix, "product-api-" + suffix, 0, true));
    Product product =
        new Product(
            category, name, name + " 짧은 설명", description, petType, thumbnailUrl, displayStatus);
    return productRepository.save(product);
  }

  private void saveSku(
      Product product, String name, String price, boolean subscribable, int displayOrder) {
    skuRepository.save(
        com.pawcycle.backend.support.TestSkuFactory.sku(
            product, name, new BigDecimal(price), subscribable, displayOrder));
  }

  private void flushAndResetStatistics() {
    entityManager.flush();
    entityManager.clear();
    statistics.clear();
  }
}
