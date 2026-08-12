package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class AdminCatalogConcurrencyIntegrationTests {
	private final WebApplicationContext applicationContext;
	private final ObjectMapper objectMapper;
	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;
	private final JdbcTemplate jdbc;
	private MockMvc mockMvc;

	@Autowired
	AdminCatalogConcurrencyIntegrationTests(
			WebApplicationContext applicationContext,
			ObjectMapper objectMapper,
			CategoryRepository categoryRepository,
			ProductRepository productRepository,
			SkuRepository skuRepository,
			JdbcTemplate jdbc) {
		this.applicationContext = applicationContext;
		this.objectMapper = objectMapper;
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
		this.jdbc = jdbc;
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
		cleanCatalog();
	}

	@AfterEach
	void tearDown() {
		cleanCatalog();
	}

	@Test
	void concurrentCategoryAndSkuDuplicatesReturnOneCreatedAndOneConflict() throws Exception {
		assertConcurrentResults(
				() -> postJson("/api/admin/categories", """
						{"name":"동시 카테고리","slug":"concurrent-category","displayOrder":1,"active":true}
						"""),
				"CATEGORY_SLUG_CONFLICT");

		long categoryId = categoryRepository.saveAndFlush(new Category("concurrent-product", "concurrent-product", 0, true)).getId();
		long productId = objectMapper.readTree(postJson("/api/admin/products", """
				{"name":"동시 상품","shortDescription":"동시 생성 테스트","description":null,
				 "petType":"DOG","thumbnailUrl":null}
				""".replace("null}", "null,\"categoryId\":" + categoryId + "}")).getResponse().getContentAsByteArray()).get("productId").asLong();

		assertConcurrentResults(
				() -> postJson("/api/admin/products/" + productId + "/skus", """
						{"skuCode":"CONCURRENT-SKU","name":"동시 SKU","price":1000.00,
						 "subscribable":true,"displayOrder":1,"status":"ACTIVE"}
						"""),
				"SKU_CODE_CONFLICT");
	}

	@Test
	void concurrentIdenticalProductTransitionsReturnOneSuccessAndOneConflict() throws Exception {
		long categoryId = categoryRepository.saveAndFlush(new Category("concurrent-transition", "concurrent-transition", 0, true)).getId();
		long productId = objectMapper.readTree(postJson("/api/admin/products", """
				{"name":"전이 상품","shortDescription":"동시 전이 테스트","description":null,
				 "petType":"DOG","thumbnailUrl":null}
				""".replace("null}", "null,\"categoryId\":" + categoryId + "}")).getResponse().getContentAsByteArray()).get("productId").asLong();

		assertConcurrentResults(
				() -> patchJson("/api/admin/products/" + productId, "{\"status\":\"PUBLIC\"}"),
				"PRODUCT_STATUS_TRANSITION_CONFLICT",
				200);
	}

	private void assertConcurrentResults(Request request, String conflictCode) throws Exception {
		assertConcurrentResults(request, conflictCode, 201);
	}

	private void assertConcurrentResults(Request request, String conflictCode, int successStatus) throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<MvcResult> results = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			List<Future<MvcResult>> futures = List.of(
					executor.submit(() -> executeTogether(request, ready, start)),
					executor.submit(() -> executeTogether(request, ready, start)));
			ready.await();
			start.countDown();
			for (Future<MvcResult> future : futures) results.add(future.get());
		}

		assertThat(results).extracting(result -> result.getResponse().getStatus())
				.containsExactlyInAnyOrder(successStatus, 409);
		MvcResult conflict = results.stream()
				.filter(result -> result.getResponse().getStatus() == 409)
				.findFirst()
				.orElseThrow();
		JsonNode body = objectMapper.readTree(conflict.getResponse().getContentAsByteArray());
		assertThat(body.get("code").asText()).isEqualTo(conflictCode);
	}

	private MvcResult executeTogether(Request request, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await();
		return request.perform();
	}

	private MvcResult postJson(String path, String body) throws Exception {
		return performJson(post(path), body);
	}

	private MvcResult patchJson(String path, String body) throws Exception {
		return performJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path), body);
	}

	private MvcResult performJson(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String body) throws Exception {
		return mockMvc.perform(request
					.with(authentication(new UsernamePasswordAuthenticationToken(
							new AuthenticatedMemberPrincipal(1L, MemberRole.ADMIN),
							null,
							List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andReturn();
	}

	private void cleanCatalog() {
		jdbc.update("DELETE FROM inventory_movements");
		jdbc.update("DELETE FROM inventories");
		skuRepository.deleteAllInBatch();
		productRepository.deleteAllInBatch();
		categoryRepository.deleteAllInBatch();
	}

	@FunctionalInterface
	private interface Request {
		MvcResult perform() throws Exception;
	}
}
