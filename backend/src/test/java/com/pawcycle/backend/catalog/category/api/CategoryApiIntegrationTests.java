package com.pawcycle.backend.catalog.category.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
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
class CategoryApiIntegrationTests {
	private final WebApplicationContext applicationContext;
	private final CategoryRepository categoryRepository;
	private MockMvc mockMvc;

	@Autowired
	CategoryApiIntegrationTests(WebApplicationContext applicationContext, CategoryRepository categoryRepository) {
		this.applicationContext = applicationContext;
		this.categoryRepository = categoryRepository;
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
	}

	@Test
	void publicCategoriesExposeOnlyActiveCategoriesInDisplayOrder() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Category first = categoryRepository.saveAndFlush(new Category("First " + suffix, "first-" + suffix, -101, true));
		Category second = categoryRepository.saveAndFlush(new Category("Second " + suffix, "second-" + suffix, -100, true));
		Category inactive = categoryRepository.saveAndFlush(new Category("Hidden " + suffix, "hidden-" + suffix, -102, false));

		mockMvc.perform(get("/api/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].categoryId").value(first.getId()))
				.andExpect(jsonPath("$.items[0].name").value(first.getName()))
				.andExpect(jsonPath("$.items[0].slug").value(first.getSlug()))
				.andExpect(jsonPath("$.items[1].categoryId").value(second.getId()))
				.andExpect(jsonPath("$.items[1].slug").value(second.getSlug()))
				.andExpect(jsonPath("$.items[?(@.categoryId == %d)]".formatted(inactive.getId())).isEmpty());
	}
}
