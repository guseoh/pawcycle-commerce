package com.pawcycle.backend.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InteractionIntegrationTests {
	@Autowired private WebApplicationContext applicationContext;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MemberRepository members;
	@Autowired private CategoryRepository categories;
	@Autowired private ProductRepository products;
	private MockMvc mockMvc;
	private Member member;
	private long productId;
	private long petId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
		member = members.saveAndFlush(new Member("interaction-" + UUID.randomUUID() + "@example.test", "fixture-password"));
		Category category = categories.saveAndFlush(new Category("interaction-" + UUID.randomUUID(), "interaction-" + UUID.randomUUID(), 0, true));
		Product product = new Product(category, "Interaction product", "fixture", null, "DOG", null);
		product.transitionTo(com.pawcycle.backend.catalog.product.domain.ProductStatus.PUBLIC);
		productId = products.saveAndFlush(product).getId();
		jdbc.update("INSERT INTO pets(member_id,name,pet_type) VALUES (?,?,?)", member.getId(), "반려동물", "DOG");
		petId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	@Test
	void authenticatedBatchPersistsServerOwnedEventAndDuplicateRetryIsIdempotent() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String body = objectMapper.writeValueAsString(Map.of("events", List.of(Map.of(
				"eventId", eventId,
				"type", "PRODUCT_VIEW",
				"productId", productId,
				"petId", petId,
				"memberId", 999999L,
				"context", Map.of("hasTextQuery", true, "petType", "DOG", "category", "interaction")))));

		postEvents(body).andExpect(status().isNoContent());
		postEvents(body).andExpect(status().isNoContent());

		Map<String, Object> event = jdbc.queryForMap("SELECT member_id,occurred_at,context FROM interaction_events WHERE event_id=?", eventId);
		assertThat(event.get("MEMBER_ID")).isEqualTo(member.getId());
		assertThat(event.get("OCCURRED_AT")).isNotNull();
		assertThat(event.get("CONTEXT").toString()).contains("hasTextQuery", "interaction");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interaction_events WHERE member_id=? AND event_id=?", Integer.class, member.getId(), eventId)).isEqualTo(1);
	}

	@Test
	void invalidLaterEventRollsBackEarlierEventFromSameBatch() throws Exception {
		String firstEvent = UUID.randomUUID().toString();
		String body = objectMapper.writeValueAsString(Map.of("events", List.of(
			Map.of("eventId", firstEvent, "type", "PRODUCT_VIEW", "productId", productId),
			Map.of("eventId", UUID.randomUUID().toString(), "type", "PRODUCT_VIEW", "productId", Long.MAX_VALUE))));

		postEvents(body).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interaction_events WHERE member_id=?", Integer.class, member.getId())).isZero();
	}

	@Test
	void authenticationCsrfAndPetOwnershipAreEnforced() throws Exception {
		String body = objectMapper.writeValueAsString(Map.of("events", List.of(Map.of(
				"eventId", UUID.randomUUID().toString(), "type", "PRODUCT_VIEW", "productId", productId, "petId", Long.MAX_VALUE))));

		mockMvc.perform(post("/api/interactions").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/interactions").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		postEvents(body).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PET_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.ResultActions postEvents(String body) throws Exception {
		return mockMvc.perform(post("/api/interactions").with(authenticated()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor authenticated() {
		return authentication(new UsernamePasswordAuthenticationToken(
				new AuthenticatedMemberPrincipal(member.getId()), null,
				List.of(new SimpleGrantedAuthority("ROLE_USER"))));
	}
}
