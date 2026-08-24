package com.pawcycle.backend.catalog.product.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductDiscoveryApiIntegrationTests {
	private final WebApplicationContext applicationContext;
	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final EntityManager entityManager;
	private MockMvc mockMvc;

	@Autowired
	ProductDiscoveryApiIntegrationTests(
			WebApplicationContext applicationContext,
			CategoryRepository categoryRepository,
			ProductRepository productRepository,
			EntityManager entityManager) {
		this.applicationContext = applicationContext;
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.entityManager = entityManager;
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
	}

	@Test
	void categoryFieldsAndDiscoveryFiltersAreCaseInsensitive() throws Exception {
		Category category = categoryRepository.saveAndFlush(new Category(
				"Dog Food " + UUID.randomUUID(), "dog-food-" + UUID.randomUUID(), 0, true));
		Product product = productRepository.save(new Product(
				category, "Dog Food", "Daily DOG food", null, "DOG", null, "PUBLIC"));
		entityManager.flush();
		entityManager.clear();

		mockMvc.perform(get("/api/products")
					.param("q", "dOg FoOd")
					.param("petType", "dog")
					.param("category", category.getSlug().toUpperCase(java.util.Locale.ROOT)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products.length()").value(1))
				.andExpect(jsonPath("$.products[0].productId").value(product.getId()))
				.andExpect(jsonPath("$.products[0].category.categoryId").value(category.getId()))
				.andExpect(jsonPath("$.products[0].category.name").value(category.getName()))
				.andExpect(jsonPath("$.products[0].category.slug").value(category.getSlug()));

		mockMvc.perform(get("/api/products/{productId}", product.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.category.categoryId").value(category.getId()))
				.andExpect(jsonPath("$.category.name").value(category.getName()))
				.andExpect(jsonPath("$.category.slug").value(category.getSlug()));
	}
}
