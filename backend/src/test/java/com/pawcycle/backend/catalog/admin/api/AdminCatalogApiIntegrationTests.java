package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.admin.api.CategoryFacetAssignRequest;
import com.pawcycle.backend.catalog.admin.api.FacetDefinitionCreateRequest;
import com.pawcycle.backend.catalog.admin.api.FacetOptionCreateRequest;
import com.pawcycle.backend.catalog.admin.api.ProductCreateRequest;
import com.pawcycle.backend.catalog.admin.api.ProductFacetValuesRequest;
import com.pawcycle.backend.catalog.admin.api.ProductPatchRequest;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogConflictException;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminPersistence;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.ProductStatus;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class AdminCatalogApiIntegrationTests {
  private final WebApplicationContext applicationContext;
  private final ObjectMapper objectMapper;
  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final SkuRepository skuRepository;
  private final JdbcTemplate jdbc;
  private final EntityManager entityManager;
  private final Statistics statistics;
  private final AdminCatalogService adminCatalogService;
  private final CatalogAdminPersistence catalogExpansionAdminService;
  private MockMvc mockMvc;

  @Autowired
  AdminCatalogApiIntegrationTests(
      WebApplicationContext applicationContext,
      ObjectMapper objectMapper,
      CategoryRepository categoryRepository,
      ProductRepository productRepository,
      SkuRepository skuRepository,
      JdbcTemplate jdbc,
      EntityManager entityManager,
      EntityManagerFactory entityManagerFactory,
      AdminCatalogService adminCatalogService,
      CatalogAdminPersistence catalogExpansionAdminService) {
    this.applicationContext = applicationContext;
    this.objectMapper = objectMapper;
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.skuRepository = skuRepository;
    this.jdbc = jdbc;
    this.entityManager = entityManager;
    this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    this.adminCatalogService = adminCatalogService;
    this.catalogExpansionAdminService = catalogExpansionAdminService;
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
    jdbc.update(
        "INSERT IGNORE INTO members(id,email,password_hash,role) VALUES"
            + " (1,'admin-catalog-fixture@example.test','fixture','ADMIN')");
  }

  @Test
  void adminBoundaryReturnsAnonymous401User403AllowsAdminAndKeepsCsrf() throws Exception {
    mockMvc
        .perform(get("/api/admin/categories"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

    mockMvc
        .perform(get("/api/admin/categories").with(role(MemberRole.USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    mockMvc
        .perform(get("/api/admin/categories").with(role(MemberRole.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories").isArray());

    mockMvc
        .perform(
            post("/api/admin/categories")
                .with(role(MemberRole.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryJson("csrf-category", true)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  }

  @Test
  void categoryCrudCoversValidationNotFoundAndSlugConflict() throws Exception {
    MvcResult created =
        createCategory("food", true)
            .andExpect(status().isCreated())
            .andExpect(
                header()
                    .string("Location", org.hamcrest.Matchers.startsWith("/api/admin/categories/")))
            .andExpect(jsonPath("$.slug").value("food"))
            .andReturn();
    long categoryId = json(created).get("categoryId").asLong();

    mockMvc
        .perform(
            patch("/api/admin/categories/{categoryId}", categoryId)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"사료\",\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("사료"))
        .andExpect(jsonPath("$.active").value(false));

    createCategory("food", true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CATEGORY_SLUG_CONFLICT"));

    mockMvc
        .perform(
            post("/api/admin/categories")
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryJson("Invalid Slug", true)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("slug"));

    mockMvc
        .perform(
            get("/api/admin/categories/{categoryId}", Long.MAX_VALUE).with(role(MemberRole.ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
  }

  @Test
  void categoryHierarchyRejectsThirdDepthOnCreateAndUpdate() throws Exception {
    long topId = json(createCategory("hierarchy-top", true).andReturn()).get("categoryId").asLong();
    long secondId =
        json(mockMvc
                .perform(
                    post("/api/admin/categories")
                        .with(role(MemberRole.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"name\":\"둘째\",\"slug\":\"hierarchy-second\",\"displayOrder\":1,\"active\":true,\"parentId\":%d}"
                                .formatted(topId)))
                .andExpect(status().isCreated())
                .andReturn())
            .get("categoryId")
            .asLong();

    mockMvc
        .perform(
            post("/api/admin/categories")
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"셋째\",\"slug\":\"hierarchy-third\",\"displayOrder\":1,\"active\":true,\"parentId\":%d}"
                        .formatted(secondId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CATEGORY_DEPTH_EXCEEDED"));

    long candidateId =
        json(createCategory("hierarchy-candidate", true).andReturn()).get("categoryId").asLong();
    mockMvc
        .perform(
            patch("/api/admin/categories/{categoryId}", candidateId)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":%d}".formatted(secondId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CATEGORY_DEPTH_EXCEEDED"));

    mockMvc
        .perform(
            patch("/api/admin/categories/{categoryId}", topId)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":%d}".formatted(secondId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CATEGORY_PARENT_CONFLICT"));
  }

  @Test
  void productCrudRequiresActiveCategoryAndEnforcesTransitions() throws Exception {
    long categoryId = json(createCategory("care", true).andReturn()).get("categoryId").asLong();
    MvcResult created =
        mockMvc
            .perform(
                post("/api/admin/products")
                    .with(role(MemberRole.ADMIN))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "categoryId": %d,
                          "brandId": 1,
                          "name": "샴푸",
                          "shortDescription": "민감성 샴푸",
                          "description": "상세",
                          "petType": "DOG",
                          "thumbnailUrl": null
                        }
                        """
                            .formatted(categoryId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.categoryId").value(categoryId))
            .andReturn();
    long productId = json(created).get("productId").asLong();

    patchProduct(productId, "{\"status\":\"PUBLIC\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLIC"));
    patchProduct(productId, "{\"status\":\"DRAFT\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRODUCT_STATUS_TRANSITION_CONFLICT"));
    patchProduct(productId, "{\"status\":\"INACTIVE\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
    patchProduct(productId, "{\"categoryId\":null}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    assertThat(productRepository.findById(productId).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.INACTIVE);
  }

  @Test
  void productCreateRequiresExplicitBrandId() throws Exception {
    long categoryId =
        json(createCategory("brand-required", true).andReturn()).get("categoryId").asLong();
    mockMvc
        .perform(
            post("/api/admin/products")
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"categoryId":%d,"name":"브랜드 필수","shortDescription":"설명","description":null,"petType":"DOG","thumbnailUrl":null}
                    """
                        .formatted(categoryId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("brandId"));

    mockMvc
        .perform(
            post("/api/admin/products")
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"categoryId":%d,"brandId":1,"name":"브랜드 명시","shortDescription":"설명","description":null,"petType":"DOG","thumbnailUrl":null}
                    """
                        .formatted(categoryId)))
        .andExpect(status().isCreated());
  }

  @Test
  void productOptionGroupsRejectThirdGroup() throws Exception {
    long productId = createProductWithoutCategory();
    for (int i = 1; i <= 2; i++) {
      mockMvc
          .perform(
              post("/api/admin/products/{productId}/option-groups", productId)
                  .with(role(MemberRole.ADMIN))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"옵션 그룹 %d\",\"displayOrder\":%d}".formatted(i, i)))
          .andExpect(status().isCreated());
    }
    mockMvc
        .perform(
            post("/api/admin/products/{productId}/option-groups", productId)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"옵션 그룹 3\",\"displayOrder\":3}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPTION_GROUP_LIMIT_EXCEEDED"));
  }

  @Test
  void productFacetValuesBlockCategoryChangeAndCategoryFacetRemoval() {
    Category first =
        categoryRepository.saveAndFlush(new Category("facet-first", "facet-first", 1, true));
    Category second =
        categoryRepository.saveAndFlush(new Category("facet-second", "facet-second", 2, true));
    long productId =
        adminCatalogService
            .createProduct(
                new ProductCreateRequest(first.getId(), 1L, "Facet 상품", "설명", null, "DOG", null))
            .productId();
    var definition =
        catalogExpansionAdminService.createFacetDefinition(
            new FacetDefinitionCreateRequest("material", "Material"));
    var option =
        catalogExpansionAdminService.createFacetOption(
            definition.facetDefinitionId(), new FacetOptionCreateRequest("cotton", 0));
    catalogExpansionAdminService.assignCategoryFacet(
        first.getId(), definition.facetDefinitionId(), new CategoryFacetAssignRequest(0));
    catalogExpansionAdminService.setProductFacetValues(
        productId, new ProductFacetValuesRequest(List.of(option.facetOptionId())));

    ProductPatchRequest categoryPatch = new ProductPatchRequest();
    categoryPatch.readCategoryId(second.getId());
    assertThatThrownBy(() -> adminCatalogService.updateProduct(productId, categoryPatch))
        .isInstanceOfSatisfying(
            AdminCatalogConflictException.class,
            exception ->
                assertThat(exception.getCode()).isEqualTo("PRODUCT_FACET_CATEGORY_CONFLICT"));
    assertThat(productRepository.findById(productId).orElseThrow().getCategory().getId())
        .isEqualTo(first.getId());

    assertThatThrownBy(
            () ->
                catalogExpansionAdminService.removeCategoryFacet(
                    first.getId(), definition.facetDefinitionId()))
        .isInstanceOfSatisfying(
            AdminCatalogConflictException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("CATEGORY_FACET_IN_USE"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM category_facets WHERE category_id=? AND"
                    + " facet_definition_id=?",
                Long.class,
                first.getId(),
                definition.facetDefinitionId()))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_facet_values WHERE product_id=? AND"
                    + " facet_option_id=?",
                Long.class,
                productId,
                option.facetOptionId()))
        .isEqualTo(1L);
  }

  @Test
  void productListFetchesDistinctCategoriesInOneQuery() throws Exception {
    Category first = categoryRepository.save(new Category("첫 카테고리", "first-category", 1, true));
    Category second = categoryRepository.save(new Category("둘째 카테고리", "second-category", 2, true));
    productRepository.save(
        new com.pawcycle.backend.catalog.product.domain.Product(
            first, "첫 상품", "첫 설명", null, "DOG", null));
    productRepository.save(
        new com.pawcycle.backend.catalog.product.domain.Product(
            second, "둘째 상품", "둘째 설명", null, "CAT", null));
    entityManager.flush();
    entityManager.clear();
    statistics.clear();

    mockMvc
        .perform(get("/api/admin/products").with(role(MemberRole.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products[0].categoryId").value(first.getId()))
        .andExpect(jsonPath("$.products[1].categoryId").value(second.getId()));

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  void skuCrudCoversDuplicateImmutableCodeValidationAndScopedNotFound() throws Exception {
    long productId = createProductWithoutCategory();
    MvcResult created =
        createSku(productId, "DOG-FOOD-2KG")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();
    long skuId = json(created).get("skuId").asLong();

    createSku(productId, "DOG-FOOD-2KG")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SKU_CODE_CONFLICT"));

    mockMvc
        .perform(
            patch("/api/admin/products/{productId}/skus/{skuId}", productId, skuId)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":21000.50,\"status\":\"INACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skuCode").value("DOG-FOOD-2KG"))
        .andExpect(jsonPath("$.price").value(21000.50))
        .andExpect(jsonPath("$.status").value("INACTIVE"));

    mockMvc
        .perform(
            patch("/api/admin/products/{productId}/skus/{skuId}", productId, skuId)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuCode\":\"CHANGED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertThat(skuRepository.findById(skuId).orElseThrow().getSkuCode()).isEqualTo("DOG-FOOD-2KG");

    long otherProductId = createProductWithoutCategory();
    mockMvc
        .perform(
            patch("/api/admin/products/{productId}/skus/{skuId}", otherProductId, skuId)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));
  }

  @Test
  void malformedAndEmptyPatchReturnStableValidationShape() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/categories")
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors").isArray());

    mockMvc
        .perform(
            patch("/api/admin/categories/{categoryId}", Long.MAX_VALUE)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("request"));
  }

  @Test
  void adminReadbacksReturnEmptyAndThenDeterministicallyPersistedAssignments() throws Exception {
    long categoryId =
        json(createCategory("readback-category-" + System.nanoTime(), true).andReturn())
            .get("categoryId")
            .asLong();
    long productId =
        json(mockMvc
                .perform(
                    post("/api/admin/products")
                        .with(role(MemberRole.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"categoryId\":%d,\"brandId\":1,\"name\":\"Readback 상품\",\"shortDescription\":\"설명\",\"petType\":\"DOG\"}"
                                .formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn())
            .get("productId")
            .asLong();

    long groupOne =
        json(mockMvc
                .perform(
                    post("/api/admin/products/{productId}/option-groups", productId)
                        .with(role(MemberRole.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"크기\",\"displayOrder\":2}"))
                .andExpect(status().isCreated())
                .andReturn())
            .get("optionGroupId")
            .asLong();
    long groupTwo =
        json(mockMvc
                .perform(
                    post("/api/admin/products/{productId}/option-groups", productId)
                        .with(role(MemberRole.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"색상\",\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andReturn())
            .get("optionGroupId")
            .asLong();
    long valueOne =
        json(mockMvc
                .perform(
                    post(
                            "/api/admin/products/{productId}/option-groups/{groupId}/values",
                            productId,
                            groupOne)
                        .with(role(MemberRole.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"대형\",\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andReturn())
            .get("optionValueId")
            .asLong();
    long valueTwo =
        json(mockMvc
                .perform(
                    post(
                            "/api/admin/products/{productId}/option-groups/{groupId}/values",
                            productId,
                            groupTwo)
                        .with(role(MemberRole.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"검정\",\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andReturn())
            .get("optionValueId")
            .asLong();
    long skuId =
        json(createSku(productId, "READBACK-SKU-" + System.nanoTime())
                .andExpect(status().isCreated())
                .andReturn())
            .get("skuId")
            .asLong();

    mockMvc
        .perform(
            get("/api/admin/products/{productId}/skus/{skuId}/option-values", productId, skuId)
                .with(role(MemberRole.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optionValueIds").isEmpty());
    mockMvc
        .perform(
            put("/api/admin/products/{productId}/skus/{skuId}/option-values", productId, skuId)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"optionValueIds\":[%d,%d]}".formatted(valueTwo, valueOne)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/admin/products/{productId}/skus/{skuId}/option-values", productId, skuId)
                .with(role(MemberRole.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optionValueIds[0]").value(valueTwo))
        .andExpect(jsonPath("$.optionValueIds[1]").value(valueOne));

    long definitionId =
        json(mockMvc
                .perform(
                    post("/api/admin/facets")
                        .with(role(MemberRole.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"readback-facet\",\"name\":\"읽기\"}"))
                .andExpect(status().isCreated())
                .andReturn())
            .get("facetDefinitionId")
            .asLong();
    long optionId =
        json(mockMvc
                .perform(
                    post("/api/admin/facets/{definitionId}/options", definitionId)
                        .with(role(MemberRole.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"신선\",\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andReturn())
            .get("facetOptionId")
            .asLong();
    mockMvc
        .perform(
            get("/api/admin/products/{productId}/facet-values", productId)
                .with(role(MemberRole.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.facetOptionIds").isEmpty());
    mockMvc
        .perform(
            put(
                    "/api/admin/categories/{categoryId}/facets/{definitionId}",
                    categoryId,
                    definitionId)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayOrder\":3}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/admin/products/{productId}/facet-values", productId)
                .with(role(MemberRole.ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"facetOptionIds\":[%d]}".formatted(optionId)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/admin/products/{productId}/facet-values", productId)
                .with(role(MemberRole.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.facetOptionIds[0]").value(optionId));
    mockMvc
        .perform(
            get("/api/admin/categories/{categoryId}/facets", categoryId)
                .with(role(MemberRole.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.facets[0].categoryId").value(categoryId))
        .andExpect(jsonPath("$.facets[0].facetDefinitionId").value(definitionId))
        .andExpect(jsonPath("$.facets[0].displayOrder").value(3));
    mockMvc
        .perform(get("/api/admin/products/{productId}/facet-values", productId))
        .andExpect(status().isUnauthorized());
  }

  private org.springframework.test.web.servlet.ResultActions createCategory(
      String slug, boolean active) throws Exception {
    return mockMvc.perform(
        post("/api/admin/categories")
            .with(role(MemberRole.ADMIN))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(categoryJson(slug, active)));
  }

  private String categoryJson(String slug, boolean active) throws Exception {
    return objectMapper.writeValueAsString(
        java.util.Map.of("name", "카테고리", "slug", slug, "displayOrder", 1, "active", active));
  }

  private long createProductWithoutCategory() throws Exception {
    long categoryId =
        json(createCategory("sku-category-" + System.nanoTime(), true).andReturn())
            .get("categoryId")
            .asLong();
    MvcResult result =
        mockMvc
            .perform(
                post("/api/admin/products")
                    .with(role(MemberRole.ADMIN))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId":%d,"brandId":1,"name":"사료","shortDescription":"기본 사료","description":null,
                         "petType":"DOG","thumbnailUrl":null}
                        """
                            .formatted(categoryId)))
            .andExpect(status().isCreated())
            .andReturn();
    return json(result).get("productId").asLong();
  }

  private org.springframework.test.web.servlet.ResultActions createSku(
      long productId, String skuCode) throws Exception {
    return mockMvc.perform(
        post("/api/admin/products/{productId}/skus", productId)
            .with(role(MemberRole.ADMIN))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"skuCode":"%s","name":"2kg","price":19900.00,
                 "subscribable":true,"displayOrder":1,"status":"ACTIVE"}
                """
                    .formatted(skuCode)));
  }

  private org.springframework.test.web.servlet.ResultActions patchProduct(
      long productId, String body) throws Exception {
    return mockMvc.perform(
        patch("/api/admin/products/{productId}", productId)
            .with(role(MemberRole.ADMIN))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private RequestPostProcessor role(MemberRole role) {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            new AuthenticatedMemberPrincipal(1L, role),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsByteArray());
  }
}
