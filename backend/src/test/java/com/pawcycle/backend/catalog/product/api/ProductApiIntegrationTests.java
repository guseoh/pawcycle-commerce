package com.pawcycle.backend.catalog.product.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
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
	private final SkuRepository skuRepository;
	private final EntityManager entityManager;
	private final Statistics statistics;
	private MockMvc mockMvc;

	@Autowired
	ProductApiIntegrationTests(
			WebApplicationContext applicationContext,
			ProductRepository productRepository,
			SkuRepository skuRepository,
			EntityManager entityManager,
			EntityManagerFactory entityManagerFactory) {
		this.applicationContext = applicationContext;
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
		this.entityManager = entityManager;
		this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
				.apply(springSecurity())
				.build();
	}

	@Test
	void anonymousListMatchesShapeOrderEmptyValuesAndUsesTwoQueries() throws Exception {
		Product first = saveProduct("첫 상품", "DOG", null, null, "PUBLIC");
		Product second = saveProduct("둘째 상품", "CAT", "상세", "https://example.test/cat.png", "PUBLIC");
		saveSku(first, "동률 뒤 SKU", "39000.00", false, 2);
		saveSku(first, "첫 SKU", "19000.00", true, 1);
		saveSku(first, "동률 앞 SKU", "29000.00", false, 2);
		flushAndResetStatistics();

		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products.length()").value(2))
				.andExpect(jsonPath("$.products[0].productId").value(first.getId()))
				.andExpect(jsonPath("$.products[0].petType").value("DOG"))
				.andExpect(jsonPath("$.products[0].thumbnailUrl").value(nullValue()))
				.andExpect(jsonPath("$.products[0].skuPriceSummary.skuPrices[0].skuName").value("첫 SKU"))
				.andExpect(jsonPath("$.products[0].skuPriceSummary.skuPrices[1].skuName").value("동률 뒤 SKU"))
				.andExpect(jsonPath("$.products[0].skuPriceSummary.skuPrices[2].skuName").value("동률 앞 SKU"))
				.andExpect(jsonPath("$.products[0].skuPriceSummary.skuPrices[0].price").isNumber())
				.andExpect(jsonPath("$.products[0].hasSubscribableSku").value(true))
				.andExpect(jsonPath("$.products[1].productId").value(second.getId()))
				.andExpect(jsonPath("$.products[1].skuPriceSummary.skuPrices").isEmpty())
				.andExpect(jsonPath("$.products[1].hasSubscribableSku").value(false))
				.andExpect(jsonPath("$.products[0].displayStatus").doesNotExist());

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
	}

	@Test
	void anonymousEmptyListReturnsEmptyArrayAndUsesOneQuery() throws Exception {
		flushAndResetStatistics();

		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products").isArray())
				.andExpect(jsonPath("$.products").isEmpty());

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void authenticatedDetailNeedsNoCsrfAndUsesTwoQueries() throws Exception {
		Product product = saveProduct("상세 상품", "DOG", null, null, "PUBLIC");
		saveSku(product, "구독 SKU", "19900.00", true, 1);
		saveSku(product, "일반 SKU", "29900.00", false, 2);
		flushAndResetStatistics();

		mockMvc.perform(get("/api/products/{productId}", product.getId())
					.with(authentication(new UsernamePasswordAuthenticationToken(
							new AuthenticatedMemberPrincipal(1L), null, List.of()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productId").value(product.getId()))
				.andExpect(jsonPath("$.description").value(nullValue()))
				.andExpect(jsonPath("$.thumbnailUrl").value(nullValue()))
				.andExpect(jsonPath("$.skus[0].subscribable").value(true))
				.andExpect(jsonPath("$.skus[0].availableDeliveryCycles.length()").value(3))
				.andExpect(jsonPath("$.skus[0].availableDeliveryCycles[0]").value(2))
				.andExpect(jsonPath("$.skus[0].availableDeliveryCycles[1]").value(4))
				.andExpect(jsonPath("$.skus[0].availableDeliveryCycles[2]").value(8))
				.andExpect(jsonPath("$.skus[1].subscribable").value(false))
				.andExpect(jsonPath("$.skus[1].availableDeliveryCycles").isEmpty())
				.andExpect(jsonPath("$.displayStatus").doesNotExist());

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
	}

	@Test
	void anonymousDetailAndAuthenticatedListArePublicWithoutCsrf() throws Exception {
		Product product = saveProduct("공개 접근 상품", "CAT", "상세", null, "PUBLIC");
		flushAndResetStatistics();

		mockMvc.perform(get("/api/products/{productId}", product.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skus").isEmpty());

		mockMvc.perform(get("/api/products")
					.with(authentication(new UsernamePasswordAuthenticationToken(
							new AuthenticatedMemberPrincipal(1L), null, List.of()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products[0].productId").value(product.getId()));
	}

	@Test
	void onlyExactUppercasePublicIsExposed() throws Exception {
		Product visible = saveProduct("공개", "DOG", null, null, "PUBLIC");
		Product lower = saveProduct("소문자", "DOG", null, null, "public");
		Product mixed = saveProduct("혼합", "DOG", null, null, "Public");
		Product padded = saveProduct("공백", "DOG", null, null, " PUBLIC ");
		Product other = saveProduct("기타", "DOG", null, null, "HIDDEN");
		flushAndResetStatistics();

		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products.length()").value(1))
				.andExpect(jsonPath("$.products[0].productId").value(visible.getId()));

		for (Long hiddenId : List.of(lower.getId(), mixed.getId(), padded.getId(), other.getId())) {
			mockMvc.perform(get("/api/products/{productId}", hiddenId))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
					.andExpect(jsonPath("$.message").value("상품을 확인할 수 없습니다."))
					.andExpect(jsonPath("$.fieldErrors").isEmpty());
		}
	}

	@Test
	void missingAndNonPublicDetailsReturnIdenticalErrors() throws Exception {
		Product hidden = saveProduct("비공개", "CAT", null, null, "HIDDEN");
		flushAndResetStatistics();

		MvcResult hiddenResult = mockMvc.perform(get("/api/products/{productId}", hidden.getId()))
				.andExpect(status().isNotFound())
				.andReturn();
		MvcResult missingResult = mockMvc.perform(get("/api/products/{productId}", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andReturn();

		assertThat(hiddenResult.getResponse().getContentAsString())
				.isEqualTo(missingResult.getResponse().getContentAsString());
	}

	private Product saveProduct(
			String name,
			String petType,
			String description,
			String thumbnailUrl,
			String displayStatus) {
		return productRepository.save(new Product(
				name,
				name + " 짧은 설명",
				description,
				petType,
				thumbnailUrl,
				displayStatus));
	}

	private void saveSku(Product product, String name, String price, boolean subscribable, int displayOrder) {
		skuRepository.save(new Sku(product, name, new BigDecimal(price), subscribable, displayOrder));
	}

	private void flushAndResetStatistics() {
		entityManager.flush();
		entityManager.clear();
		statistics.clear();
	}
}
