package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.common.security.ApiAccessDeniedHandler;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SecurityFoundationIntegrationTests {

	private final WebApplicationContext applicationContext;
	private final ApiAccessDeniedHandler accessDeniedHandler;
	private final JdbcTemplate jdbc;
	private MockMvc mockMvc;

	@Autowired
	SecurityFoundationIntegrationTests(
			WebApplicationContext applicationContext,
			ApiAccessDeniedHandler accessDeniedHandler,
			JdbcTemplate jdbc) {
		this.applicationContext = applicationContext;
		this.accessDeniedHandler = accessDeniedHandler;
		this.jdbc = jdbc;
	}

	@BeforeEach
	void configureMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
				.apply(springSecurity())
				.build();
	}

	@Test
	void publicProductAndAuthenticationBoundariesAllowAnonymousRequests() throws Exception {
		mockMvc.perform(get("/api/products/test"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("상품을 확인할 수 없습니다."))
				.andExpect(jsonPath("$.fieldErrors").isArray())
				.andExpect(jsonPath("$.fieldErrors").isEmpty());

		mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	@Transactional
	void anonymousProductRecommendationsArePublicAndKeepResponseContract() throws Exception {
		String suffix = UUID.randomUUID().toString();
		String categorySlug = "security-recommendation-" + suffix;
		jdbc.update("INSERT INTO categories(name,slug,display_order,active) VALUES (?,?,0,true)", "추천 테스트 카테고리", categorySlug);
		Long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug=?", Long.class, categorySlug);
		long sourceId = insertProduct(categoryId, suffix + "-source", "추천 원본");
		long relatedId = insertProduct(categoryId, suffix + "-related", "추천 후보");
		insertAvailableSku(sourceId, suffix + "-source-sku");
		insertAvailableSku(relatedId, suffix + "-related-sku");

		mockMvc.perform(get("/api/products/{productId}/related", sourceId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requestId").isString())
				.andExpect(jsonPath("$.products[0].productId").value(relatedId))
				.andExpect(jsonPath("$.products[0].strategy").value("RELATED"));

			mockMvc.perform(get("/api/products/{productId}/complementary", sourceId))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.requestId").isString())
					.andExpect(jsonPath("$.products").isArray());

		mockMvc.perform(get("/api/products/{productId}/related", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	private long insertProduct(Long categoryId, String catalogKey, String name) {
		jdbc.update("INSERT INTO products(brand_id,catalog_key,category_id,name,short_description,pet_type,display_status) VALUES (1,?,?,?,?,?,'PUBLIC')",
			catalogKey, categoryId, name, name + " 짧은 설명", "DOG");
		return jdbc.queryForObject("SELECT id FROM products WHERE catalog_key=?", Long.class, catalogKey);
	}

	private void insertAvailableSku(long productId, String skuCode) {
		jdbc.update("INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES (?,?,?,1000,false,1,'ACTIVE')",
			productId, skuCode, skuCode + " 옵션");
		Long skuId = jdbc.queryForObject("SELECT id FROM skus WHERE sku_code=?", Long.class, skuCode);
		jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,10,0,0)", skuId);
	}

	@Test
	void protectedApiReturnsAuthRequiredJsonWithoutRedirect() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
				.andExpect(jsonPath("$.fieldErrors").isArray())
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull());
	}

	@Test
	void recommendationApiRequiresAuthenticationAndHidesNonOwnedPet() throws Exception {
		mockMvc.perform(get("/api/recommendations/products").param("petId", "42"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

		mockMvc.perform(get("/api/recommendations/products")
					.param("petId", String.valueOf(Long.MAX_VALUE))
					.with(authentication(new UsernamePasswordAuthenticationToken(
							new AuthenticatedMemberPrincipal(1L), null, java.util.List.of()))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PET_NOT_FOUND"));
	}

	@Test
	void anonymousLogoutWithValidCsrfReturnsAuthRequiredJsonWithoutRedirect() throws Exception {
		mockMvc.perform(post("/api/auth/logout").with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
				.andExpect(jsonPath("$.fieldErrors").isArray())
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull());
	}

	@Test
	void authenticatedRequestCanReachProtectedBoundary() throws Exception {
		mockMvc.perform(get("/api/auth/me").with(authentication(
				new UsernamePasswordAuthenticationToken(
						new AuthenticatedMemberPrincipal(1L), null, java.util.List.of()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.memberId").value(1));
	}

	@Test
	void stateChangingRequestWithoutCsrfReturnsCsrfInvalidJson() throws Exception {
		mockMvc.perform(post("/api/auth/login"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"))
				.andExpect(jsonPath("$.fieldErrors").isArray())
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	void accessDeniedHandlerReturnsAccessDeniedJson() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		accessDeniedHandler.handle(request, response, new AccessDeniedException("test-only"));

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("\"code\":\"ACCESS_DENIED\"");
		assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("\"fieldErrors\":[]");
	}
}
