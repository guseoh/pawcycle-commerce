package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.ProductStatus;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
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
	private final EntityManager entityManager;
	private final Statistics statistics;
	private MockMvc mockMvc;

	@Autowired
	AdminCatalogApiIntegrationTests(
			WebApplicationContext applicationContext,
			ObjectMapper objectMapper,
			CategoryRepository categoryRepository,
			ProductRepository productRepository,
			SkuRepository skuRepository,
			EntityManager entityManager,
			EntityManagerFactory entityManagerFactory) {
		this.applicationContext = applicationContext;
		this.objectMapper = objectMapper;
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
		this.entityManager = entityManager;
		this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
	}

	@Test
	void adminBoundaryReturnsAnonymous401User403AllowsAdminAndKeepsCsrf() throws Exception {
		mockMvc.perform(get("/api/admin/categories"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

		mockMvc.perform(get("/api/admin/categories").with(role(MemberRole.USER)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		mockMvc.perform(get("/api/admin/categories").with(role(MemberRole.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categories").isArray());

		mockMvc.perform(post("/api/admin/categories")
					.with(role(MemberRole.ADMIN))
					.contentType(MediaType.APPLICATION_JSON)
					.content(categoryJson("csrf-category", true)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	void categoryCrudCoversValidationNotFoundAndSlugConflict() throws Exception {
		MvcResult created = createCategory("food", true)
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/admin/categories/")))
				.andExpect(jsonPath("$.slug").value("food"))
				.andReturn();
		long categoryId = json(created).get("categoryId").asLong();

		mockMvc.perform(patch("/api/admin/categories/{categoryId}", categoryId)
					.with(role(MemberRole.ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"사료\",\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("사료"))
				.andExpect(jsonPath("$.active").value(false));

		createCategory("food", true)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CATEGORY_SLUG_CONFLICT"));

		mockMvc.perform(post("/api/admin/categories")
					.with(role(MemberRole.ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(categoryJson("Invalid Slug", true)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("slug"));

		mockMvc.perform(get("/api/admin/categories/{categoryId}", Long.MAX_VALUE).with(role(MemberRole.ADMIN)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
	}

	@Test
	void productCrudRequiresActiveCategoryAndEnforcesTransitions() throws Exception {
		long categoryId = json(createCategory("care", true).andReturn()).get("categoryId").asLong();
		MvcResult created = mockMvc.perform(post("/api/admin/products")
					.with(role(MemberRole.ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "categoryId": %d,
							  "name": "샴푸",
							  "shortDescription": "민감성 샴푸",
							  "description": "상세",
							  "petType": "DOG",
							  "thumbnailUrl": null
							}
							""".formatted(categoryId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.categoryId").value(categoryId))
				.andReturn();
		long productId = json(created).get("productId").asLong();

		patchProduct(productId, "{\"status\":\"PUBLIC\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLIC"));
		patchProduct(productId, "{\"status\":\"DRAFT\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PRODUCT_STATUS_TRANSITION_CONFLICT"));
		patchProduct(productId, "{\"status\":\"INACTIVE\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
		patchProduct(productId, "{\"categoryId\":null}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		assertThat(productRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(ProductStatus.PUBLIC);
	}

	@Test
	void productListFetchesDistinctCategoriesInOneQuery() throws Exception {
		Category first = categoryRepository.save(new Category("첫 카테고리", "first-category", 1, true));
		Category second = categoryRepository.save(new Category("둘째 카테고리", "second-category", 2, true));
		productRepository.save(new com.pawcycle.backend.catalog.product.domain.Product(
				first, "첫 상품", "첫 설명", null, "DOG", null));
		productRepository.save(new com.pawcycle.backend.catalog.product.domain.Product(
				second, "둘째 상품", "둘째 설명", null, "CAT", null));
		entityManager.flush();
		entityManager.clear();
		statistics.clear();

		mockMvc.perform(get("/api/admin/products").with(role(MemberRole.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products[0].categoryId").value(first.getId()))
				.andExpect(jsonPath("$.products[1].categoryId").value(second.getId()));

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void skuCrudCoversDuplicateImmutableCodeValidationAndScopedNotFound() throws Exception {
		long productId = createProductWithoutCategory();
		MvcResult created = createSku(productId, "DOG-FOOD-2KG")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andReturn();
		long skuId = json(created).get("skuId").asLong();

		createSku(productId, "DOG-FOOD-2KG")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SKU_CODE_CONFLICT"));

		mockMvc.perform(patch("/api/admin/products/{productId}/skus/{skuId}", productId, skuId)
					.with(role(MemberRole.ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"price\":21000.50,\"status\":\"INACTIVE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skuCode").value("DOG-FOOD-2KG"))
				.andExpect(jsonPath("$.price").value(21000.50))
				.andExpect(jsonPath("$.status").value("INACTIVE"));

		mockMvc.perform(patch("/api/admin/products/{productId}/skus/{skuId}", productId, skuId)
					.with(role(MemberRole.ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"skuCode\":\"CHANGED\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		assertThat(skuRepository.findById(skuId).orElseThrow().getSkuCode()).isEqualTo("DOG-FOOD-2KG");

		long otherProductId = createProductWithoutCategory();
		mockMvc.perform(patch("/api/admin/products/{productId}/skus/{skuId}", otherProductId, skuId)
					.with(role(MemberRole.ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"ACTIVE\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));
	}

	@Test
	void malformedAndEmptyPatchReturnStableValidationShape() throws Exception {
		mockMvc.perform(post("/api/admin/categories")
					.with(role(MemberRole.ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors").isArray());

		mockMvc.perform(patch("/api/admin/categories/{categoryId}", Long.MAX_VALUE)
					.with(role(MemberRole.ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("request"));
	}

	private org.springframework.test.web.servlet.ResultActions createCategory(String slug, boolean active) throws Exception {
		return mockMvc.perform(post("/api/admin/categories")
				.with(role(MemberRole.ADMIN)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(categoryJson(slug, active)));
	}

	private String categoryJson(String slug, boolean active) throws Exception {
		return objectMapper.writeValueAsString(java.util.Map.of(
				"name", "카테고리", "slug", slug, "displayOrder", 1, "active", active));
	}

	private long createProductWithoutCategory() throws Exception {
		long categoryId = json(createCategory("sku-category-" + System.nanoTime(), true).andReturn()).get("categoryId").asLong();
		MvcResult result = mockMvc.perform(post("/api/admin/products")
					.with(role(MemberRole.ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"사료","shortDescription":"기본 사료","description":null,
							 "petType":"DOG","thumbnailUrl":null}
							""".replace("null}", "null,\"categoryId\":" + categoryId + "}")))
				.andExpect(status().isCreated())
				.andReturn();
		return json(result).get("productId").asLong();
	}

	private org.springframework.test.web.servlet.ResultActions createSku(long productId, String skuCode) throws Exception {
		return mockMvc.perform(post("/api/admin/products/{productId}/skus", productId)
				.with(role(MemberRole.ADMIN)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"skuCode":"%s","name":"2kg","price":19900.00,
						 "subscribable":true,"displayOrder":1,"status":"ACTIVE"}
						""".formatted(skuCode)));
	}

	private org.springframework.test.web.servlet.ResultActions patchProduct(long productId, String body) throws Exception {
		return mockMvc.perform(patch("/api/admin/products/{productId}", productId)
				.with(role(MemberRole.ADMIN)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private RequestPostProcessor role(MemberRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new AuthenticatedMemberPrincipal(1L, role),
				null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsByteArray());
	}
}
