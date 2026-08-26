package com.pawcycle.backend.catalog.category.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.category.application.CategoryListView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryApiIntegrationTests {
	private final WebApplicationContext applicationContext;
	private final CategoryRepository categoryRepository;
	private final ObjectMapper objectMapper;
	private MockMvc mockMvc;

	@Autowired
	CategoryApiIntegrationTests(
			WebApplicationContext applicationContext,
			CategoryRepository categoryRepository,
			ObjectMapper objectMapper) {
		this.applicationContext = applicationContext;
		this.categoryRepository = categoryRepository;
		this.objectMapper = objectMapper;
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
	}

	@Test
	void publicCategoriesExposeOnlyActiveCategoriesInDisplayOrder() throws Exception {
		String suffix = UUID.randomUUID().toString();
		Category first = categoryRepository.saveAndFlush(new Category("First " + suffix, "first-" + suffix, 0, true));
		Category second = categoryRepository.saveAndFlush(new Category("Second " + suffix, "second-" + suffix, 1, true));
		Category inactive = categoryRepository.saveAndFlush(new Category("Hidden " + suffix, "hidden-" + suffix, 0, false));

		MvcResult result = mockMvc.perform(get("/api/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.categoryId == %d)]".formatted(first.getId())).exists())
				.andExpect(jsonPath("$.items[?(@.categoryId == %d)]".formatted(second.getId())).exists())
				.andExpect(jsonPath("$.items[?(@.categoryId == %d)]".formatted(inactive.getId())).isEmpty())
				.andReturn();
		CategoryListView response = objectMapper.readValue(result.getResponse().getContentAsString(), CategoryListView.class);
		List<Long> activeTestCategoryIds = response.items().stream()
				.map(CategoryListView.CategorySummary::categoryId)
				.filter(categoryId -> categoryId.equals(first.getId()) || categoryId.equals(second.getId()))
				.toList();

		assertEquals(List.of(first.getId(), second.getId()), activeTestCategoryIds);
		assertFalse(response.items().stream().anyMatch(category -> category.categoryId().equals(inactive.getId())));
	}
}
