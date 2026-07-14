package com.pawcycle.backend.subscription.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import com.pawcycle.backend.subscription.domain.Subscription;
import com.pawcycle.backend.subscription.infra.SubscriptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
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
@Import(SubscriptionApiIntegrationTests.FixedClockConfiguration.class)
class SubscriptionApiIntegrationTests {

	private static final LocalDate CREATED_DATE = LocalDate.of(2026, 7, 14);

	private final WebApplicationContext applicationContext;
	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final PasswordEncoder passwordEncoder;
	private final ObjectMapper objectMapper;
	private final EntityManager entityManager;
	private final Statistics statistics;
	private MockMvc mockMvc;
	private Member owner;
	private Member other;
	private Product product;

	@Autowired
	SubscriptionApiIntegrationTests(
			WebApplicationContext applicationContext,
			MemberRepository memberRepository,
			ProductRepository productRepository,
			SkuRepository skuRepository,
			SubscriptionRepository subscriptionRepository,
			PasswordEncoder passwordEncoder,
			ObjectMapper objectMapper,
			EntityManager entityManager,
			EntityManagerFactory entityManagerFactory) {
		this.applicationContext = applicationContext;
		this.memberRepository = memberRepository;
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.passwordEncoder = passwordEncoder;
		this.objectMapper = objectMapper;
		this.entityManager = entityManager;
		this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
				.apply(springSecurity())
				.build();
		owner = saveMember("owner");
		other = saveMember("other");
		product = productRepository.saveAndFlush(new Product(
				"반려견 사료", "반려견 사료 설명", "상세 설명", "DOG", null, "PUBLIC"));
	}

	@Test
	void createReturnsApprovedShapeUsesPrincipalOwnerAndAllowsDuplicateConditions() throws Exception {
		Sku sku = saveSku("2kg", "19900.00", true, 1);
		Map<String, Object> request = Map.of(
				"skuId", sku.getId(),
				"quantity", 2,
				"deliveryCycleWeeks", 4,
				"memberId", other.getId());

		MvcResult first = postSubscription(owner, request)
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.subscriptionId").isNumber())
				.andExpect(jsonPath("$.nextOrderDate").value("2026-08-11"))
				.andExpect(jsonPath("$.memberId").doesNotExist())
				.andReturn();
		MvcResult second = postSubscription(owner, request)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nextOrderDate").value("2026-08-11"))
				.andReturn();

		Long firstId = json(first).get("subscriptionId").asLong();
		Long secondId = json(second).get("subscriptionId").asLong();
		assertThat(secondId).isNotEqualTo(firstId);
		assertThat(subscriptionRepository.findOwnedWithCatalog(firstId, owner.getId())).isPresent();
		assertThat(subscriptionRepository.findOwnedWithCatalog(firstId, other.getId())).isEmpty();
		assertThat(subscriptionRepository.findAllOwnedWithCatalogOrderByIdDesc(owner.getId())).hasSize(2);
	}

	@Test
	void listReturnsOnlyOwnerNewestFirstAndUsesOneQuery() throws Exception {
		Sku firstSku = saveSku("2kg", "19900.00", true, 1);
		Sku secondSku = saveSku("4kg", "29900.00", true, 2);
		Subscription older = saveSubscription(owner, firstSku, 1, 4);
		Subscription newer = saveSubscription(owner, secondSku, 2, 8);
		saveSubscription(other, firstSku, 3, 2);
		flushAndResetStatistics();

		mockMvc.perform(get("/api/subscriptions").with(authenticated(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subscriptions.length()").value(2))
				.andExpect(jsonPath("$.subscriptions[0].subscriptionId").value(newer.getId()))
				.andExpect(jsonPath("$.subscriptions[0].product.productId").value(product.getId()))
				.andExpect(jsonPath("$.subscriptions[0].product.name").value("반려견 사료"))
				.andExpect(jsonPath("$.subscriptions[0].sku.skuId").value(secondSku.getId()))
				.andExpect(jsonPath("$.subscriptions[0].sku.skuName").value("4kg"))
				.andExpect(jsonPath("$.subscriptions[0].quantity").value(2))
				.andExpect(jsonPath("$.subscriptions[0].deliveryCycleWeeks").value(8))
				.andExpect(jsonPath("$.subscriptions[0].nextOrderDate").value("2026-09-08"))
				.andExpect(jsonPath("$.subscriptions[0].createdDate").doesNotExist())
				.andExpect(jsonPath("$.subscriptions[0].sku.price").doesNotExist())
				.andExpect(jsonPath("$.subscriptions[1].subscriptionId").value(older.getId()));

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void emptyListReturnsArrayAndUsesOneQuery() throws Exception {
		flushAndResetStatistics();

		mockMvc.perform(get("/api/subscriptions").with(authenticated(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subscriptions").isArray())
				.andExpect(jsonPath("$.subscriptions.length()").value(0));

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void detailReturnsCurrentPriceCreatedDateAndUsesOneQuery() throws Exception {
		Sku sku = saveSku("2kg", "19900.00", true, 1);
		Subscription subscription = saveSubscription(owner, sku, 2, 4);
		flushAndResetStatistics();

		mockMvc.perform(get("/api/subscriptions/{subscriptionId}", subscription.getId())
					.with(authenticated(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subscriptionId").value(subscription.getId()))
				.andExpect(jsonPath("$.product.productId").value(product.getId()))
				.andExpect(jsonPath("$.sku.skuId").value(sku.getId()))
				.andExpect(jsonPath("$.sku.skuName").value("2kg"))
				.andExpect(jsonPath("$.sku.price").isNumber())
				.andExpect(jsonPath("$.sku.price").value(19900.00))
				.andExpect(jsonPath("$.quantity").value(2))
				.andExpect(jsonPath("$.deliveryCycleWeeks").value(4))
				.andExpect(jsonPath("$.createdDate").value("2026-07-14"))
				.andExpect(jsonPath("$.nextOrderDate").value("2026-08-11"));

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void missingOtherOwnedAndNonNumericDetailsReturnIdenticalNotFoundBodies() throws Exception {
		Sku sku = saveSku("2kg", "19900.00", true, 1);
		Subscription otherSubscription = saveSubscription(other, sku, 1, 2);
		entityManager.flush();
		entityManager.clear();

		MvcResult missing = mockMvc.perform(get("/api/subscriptions/{subscriptionId}", Long.MAX_VALUE)
					.with(authenticated(owner)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"))
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andReturn();
		MvcResult otherOwned = mockMvc.perform(get("/api/subscriptions/{subscriptionId}", otherSubscription.getId())
					.with(authenticated(owner)))
				.andExpect(status().isNotFound())
				.andReturn();
		MvcResult nonNumeric = mockMvc.perform(get("/api/subscriptions/not-a-number")
					.with(authenticated(owner)))
				.andExpect(status().isNotFound())
				.andReturn();

		assertThat(otherOwned.getResponse().getContentAsString())
				.isEqualTo(missing.getResponse().getContentAsString());
		assertThat(nonNumeric.getResponse().getContentAsString())
				.isEqualTo(missing.getResponse().getContentAsString());
	}

	@Test
	void validationTypeAndSkuErrorsMatchApprovedCodes() throws Exception {
		Sku unavailable = saveSku("일반 SKU", "9900.00", false, 1);

		postSubscription(owner, Map.of("skuId", unavailable.getId(), "quantity", 0, "deliveryCycleWeeks", 6))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("입력 내용을 확인해 주세요."))
				.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("quantity")))
				.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("deliveryCycleWeeks")));

		postSubscription(owner, Map.of("quantity", 1, "deliveryCycleWeeks", 2))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("skuId")));

		mockMvc.perform(post("/api/subscriptions")
					.with(authenticated(owner))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"skuId\":\"wrong-type\",\"quantity\":1,\"deliveryCycleWeeks\":2}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("skuId"));

		mockMvc.perform(post("/api/subscriptions")
					.with(authenticated(owner))
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("request"));

		postSubscription(owner, Map.of("skuId", Long.MAX_VALUE, "quantity", 1, "deliveryCycleWeeks", 2))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"))
				.andExpect(jsonPath("$.fieldErrors").isEmpty());

		postSubscription(owner, Map.of("skuId", unavailable.getId(), "quantity", 1, "deliveryCycleWeeks", 2))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SKU_NOT_SUBSCRIBABLE"))
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	void subscriptionSecurityAndPublicProductRegressionMatchExistingSessionBoundary() throws Exception {
		Sku sku = saveSku("2kg", "19900.00", true, 1);
		Map<String, Object> request = Map.of("skuId", sku.getId(), "quantity", 1, "deliveryCycleWeeks", 2);

		mockMvc.perform(get("/api/subscriptions"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mockMvc.perform(get("/api/subscriptions/1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mockMvc.perform(post("/api/subscriptions")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mockMvc.perform(post("/api/subscriptions")
					.with(authenticated(owner))
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mockMvc.perform(post("/api/subscriptions")
					.with(authenticated(owner))
					.with(csrf().useInvalidToken())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products").isArray());
		mockMvc.perform(get("/api/products/{productId}", product.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productId").value(product.getId()));
	}

	private org.springframework.test.web.servlet.ResultActions postSubscription(
			Member member,
			Map<String, Object> request) throws Exception {
		return mockMvc.perform(post("/api/subscriptions")
				.with(authenticated(member))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));
	}

	private RequestPostProcessor authenticated(Member member) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new AuthenticatedMemberPrincipal(member.getId()), null, List.of()));
	}

	private Member saveMember(String prefix) {
		return memberRepository.saveAndFlush(new Member(
				prefix + "-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode(UUID.randomUUID().toString())));
	}

	private Sku saveSku(String name, String price, boolean subscribable, int displayOrder) {
		return skuRepository.saveAndFlush(new Sku(
				product, name, new BigDecimal(price), subscribable, displayOrder));
	}

	private Subscription saveSubscription(Member member, Sku sku, int quantity, int cycle) {
		return subscriptionRepository.saveAndFlush(Subscription.create(
				member, sku, quantity, cycle, CREATED_DATE));
	}

	private void flushAndResetStatistics() {
		entityManager.flush();
		entityManager.clear();
		statistics.clear();
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsByteArray());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedSubscriptionClock() {
			return Clock.fixed(Instant.parse("2026-07-13T15:30:00Z"), ZoneOffset.UTC);
		}
	}
}
