package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import com.pawcycle.backend.subscription.domain.Subscription;
import com.pawcycle.backend.subscription.infra.SubscriptionRepository;
import com.pawcycle.backend.subscription.v2.V2SubscriptionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LocalQaBootstrapIntegrationTests {

	private static final String OTHER_PRODUCT_PREFIX = "[TEST FOUNDATION-004] ";
	private static final String V2_PLAN_PREFIX = "[TEST FOUNDATION-004 V2] ";

	private final LocalQaBootstrapService bootstrapService;
	private final V2SubscriptionService v2SubscriptionService;
	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final SkuRepository skuRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final PasswordEncoder passwordEncoder;
	private final JdbcTemplate jdbcTemplate;

	@Autowired
	LocalQaBootstrapIntegrationTests(
			LocalQaBootstrapService bootstrapService,
			V2SubscriptionService v2SubscriptionService,
			MemberRepository memberRepository,
			ProductRepository productRepository,
			CategoryRepository categoryRepository,
			SkuRepository skuRepository,
			SubscriptionRepository subscriptionRepository,
			PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate) {
		this.bootstrapService = bootstrapService;
		this.v2SubscriptionService = v2SubscriptionService;
		this.memberRepository = memberRepository;
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.skuRepository = skuRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
	}

	@BeforeEach
	@AfterEach
	void cleanBootstrapFixtures() {
		deleteV2ChildrenForFixtureMembers();
		jdbcTemplate.update("""
				DELETE FROM subscriptions
				WHERE member_id IN (
					SELECT id FROM members
					WHERE email LIKE 'qa-foundation-004@%'
					   OR email LIKE 'other-foundation-004@%'
				)
				   OR sku_id IN (
					SELECT sku.id
					FROM skus sku
					JOIN products product ON product.id = sku.product_id
					WHERE product.name = ? OR product.name LIKE ?
				)
				""", LocalQaBootstrapService.PRODUCT_NAME, OTHER_PRODUCT_PREFIX + "%");
		jdbcTemplate.update("DELETE FROM pets WHERE member_id IN (SELECT id FROM members WHERE email LIKE 'qa-foundation-004@%' OR email LIKE 'other-foundation-004@%')");
		deleteV2FixturePlans();
		jdbcTemplate.update("""
				DELETE inventory
				FROM inventories inventory
				JOIN skus sku ON sku.id = inventory.sku_id
				JOIN products product ON product.id = sku.product_id
				WHERE product.name = ? OR product.name LIKE ?
				""", LocalQaBootstrapService.PRODUCT_NAME, OTHER_PRODUCT_PREFIX + "%");
		jdbcTemplate.update("""
				DELETE FROM skus
				WHERE product_id IN (
					SELECT id FROM products WHERE name = ? OR name LIKE ?
				)
				""", LocalQaBootstrapService.PRODUCT_NAME, OTHER_PRODUCT_PREFIX + "%");
		jdbcTemplate.update("DELETE FROM products WHERE name = ?", LocalQaBootstrapService.PRODUCT_NAME);
		jdbcTemplate.update("DELETE FROM products WHERE name LIKE ?", OTHER_PRODUCT_PREFIX + "%");
		jdbcTemplate.update("""
				DELETE FROM members
				WHERE email LIKE 'qa-foundation-004@%'
				   OR email LIKE 'other-foundation-004@%'
				""");
	}

	@Test
	void firstAndRepeatedRunCreateOneFixtureAndPreserveSubscriptionsWhenResetIsDisabled() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();

		bootstrapService.bootstrap(email, password, false);
		Member member = memberRepository.findByEmail(email).orElseThrow();
		Product product = productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME).get(0);
		Sku sku = skuRepository.findAllByProductIdAndName(product.getId(), LocalQaBootstrapService.SKU_NAME).get(0);
		subscriptionRepository.saveAndFlush(Subscription.create(member, sku, 1, 4, LocalDate.now()));

		bootstrapService.bootstrap(email, password, false);

		assertThat(memberRepository.findByEmail(email)).isPresent();
		assertThat(productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME)).hasSize(1);
		assertThat(product.getCategory().isActive()).isFalse();
		assertThat(skuRepository.findAllByProductIdAndName(product.getId(), LocalQaBootstrapService.SKU_NAME))
				.hasSize(1);
		assertThat(subscriptionCount(member.getId())).isEqualTo(1);
		assertThat(passwordEncoder.matches(password, member.getPasswordHash())).isTrue();
		assertThat(member.getPasswordHash().equals(password)).isFalse();
	}

	@Test
	void resetDeletesOnlyQaMemberSubscriptionsAndPreservesOtherData() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		bootstrapService.bootstrap(email, password, false);
		Member qaMember = memberRepository.findByEmail(email).orElseThrow();
		Product fixtureProduct = productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME).get(0);
		Sku fixtureSku = skuRepository
				.findAllByProductIdAndName(fixtureProduct.getId(), LocalQaBootstrapService.SKU_NAME)
				.get(0);

		Member otherMember = memberRepository.saveAndFlush(new Member(
				"other-foundation-004@" + UUID.randomUUID() + ".example",
				passwordEncoder.encode(UUID.randomUUID().toString())));
		Product otherProduct = productRepository.saveAndFlush(new Product(activeCategory(),
				OTHER_PRODUCT_PREFIX + UUID.randomUUID(),
				"비대상 상품",
				null,
				"CAT",
				null,
				"PUBLIC"));
		Sku otherSku = skuRepository.saveAndFlush(com.pawcycle.backend.support.TestSkuFactory.sku(
				otherProduct,
				"비대상 SKU",
				new BigDecimal("25000.00"),
				true,
				1));
		subscriptionRepository.saveAndFlush(Subscription.create(qaMember, fixtureSku, 1, 2, LocalDate.now()));
		subscriptionRepository.saveAndFlush(Subscription.create(otherMember, otherSku, 2, 8, LocalDate.now()));

		bootstrapService.bootstrap(email, password, true);

		assertThat(subscriptionCount(qaMember.getId())).isZero();
		assertThat(subscriptionCount(otherMember.getId())).isEqualTo(1);
		assertThat(memberRepository.existsById(otherMember.getId())).isTrue();
		assertThat(productRepository.existsById(otherProduct.getId())).isTrue();
		assertThat(skuRepository.existsById(otherSku.getId())).isTrue();
	}

	@Test
	void resetDeletesV2AggregateBeforeDeletingQaSubscription() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		bootstrapService.bootstrap(email, password, false);
		Member member = memberRepository.findByEmail(email).orElseThrow();
		Product product = productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME).getFirst();
		Sku sku = skuRepository.findAllByProductIdAndName(product.getId(), LocalQaBootstrapService.SKU_NAME).getFirst();
		long planVersionId = createV2Plan(sku);
		long petId = ((Number) v2SubscriptionService.createPet(member.getId(), Map.of("name", "QA 반려동물", "petType", "DOG")).get("petId")).longValue();
		V2SubscriptionService.V2Result created = v2SubscriptionService.createSubscription(member.getId(), "qa-v2-reset", Map.of("petId", petId, "planVersionId", planVersionId, "deliveryCycleWeeks", 4));
		long subscriptionId = ((Number) created.body().get("subscriptionId")).longValue();
		v2SubscriptionService.command(member.getId(), subscriptionId, "change-plan", "qa-v2-change", "\"0\"", Map.of("planVersionId", planVersionId));

		bootstrapService.bootstrap(email, password, true);

		assertThat(subscriptionCount(member.getId())).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pending_plan_changes WHERE subscription_id=?", Integer.class, subscriptionId)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=?", Integer.class, subscriptionId)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription_snapshots WHERE subscription_id=?", Integer.class, subscriptionId)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription_creation_idempotency_results WHERE member_id=?", Integer.class, member.getId())).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription_command_idempotency_results WHERE member_id=?", Integer.class, member.getId())).isZero();
	}

	@Test
	void ambiguousFixtureRollsBackMemberCreation() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		productRepository.saveAllAndFlush(List.of(exactFixtureProduct(), exactFixtureProduct()));

		assertThatThrownBy(() -> bootstrapService.bootstrap(email, password, false))
				.isInstanceOf(LocalQaBootstrapException.class);

		assertThat(memberRepository.findByEmail(email)).isEmpty();
		assertThat(productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME)).hasSize(2);
	}

	private long createV2Plan(Sku sku) {
		jdbcTemplate.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)", V2_PLAN_PREFIX + UUID.randomUUID(), "DOG");
		long planId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,19900,false)", planId);
		long planVersionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,1)", planVersionId, sku.getId());
		jdbcTemplate.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,4)", planVersionId);
		jdbcTemplate.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", planVersionId, planId);
		return planVersionId;
	}

	private void deleteV2ChildrenForFixtureMembers() {
		String memberFilter = "SELECT id FROM members WHERE email LIKE 'qa-foundation-004@%' OR email LIKE 'other-foundation-004@%'";
		jdbcTemplate.update("DELETE p FROM pending_plan_changes p JOIN subscriptions s ON s.id=p.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbcTemplate.update("DELETE r FROM subscription_command_idempotency_results r JOIN subscriptions s ON s.id=r.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbcTemplate.update("DELETE FROM subscription_creation_idempotency_results WHERE member_id IN (" + memberFilter + ")");
		jdbcTemplate.update("DELETE h FROM subscription_command_history h JOIN subscriptions s ON s.id=h.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbcTemplate.update("DELETE sc FROM subscription_schedules sc JOIN subscriptions s ON s.id=sc.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbcTemplate.update("DELETE si FROM subscription_snapshot_items si JOIN subscription_snapshots ss ON ss.id=si.snapshot_id JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
		jdbcTemplate.update("UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id IN (" + memberFilter + ")");
		jdbcTemplate.update("DELETE ss FROM subscription_snapshots ss JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id IN (" + memberFilter + ")");
	}

	private void deleteV2FixturePlans() {
		jdbcTemplate.update("UPDATE subscription_plans SET current_plan_version_id=NULL WHERE name LIKE ?", V2_PLAN_PREFIX + "%");
		jdbcTemplate.update("DELETE c FROM plan_version_delivery_cycles c JOIN plan_versions v ON v.id=c.plan_version_id JOIN subscription_plans p ON p.id=v.plan_id WHERE p.name LIKE ?", V2_PLAN_PREFIX + "%");
		jdbcTemplate.update("DELETE i FROM plan_items i JOIN plan_versions v ON v.id=i.plan_version_id JOIN subscription_plans p ON p.id=v.plan_id WHERE p.name LIKE ?", V2_PLAN_PREFIX + "%");
		jdbcTemplate.update("DELETE v FROM plan_versions v JOIN subscription_plans p ON p.id=v.plan_id WHERE p.name LIKE ?", V2_PLAN_PREFIX + "%");
		jdbcTemplate.update("DELETE FROM subscription_plans WHERE name LIKE ?", V2_PLAN_PREFIX + "%");
	}

	private Product exactFixtureProduct() {
		return new Product(activeCategory(),
				LocalQaBootstrapService.PRODUCT_NAME,
				LocalQaBootstrapService.PRODUCT_SHORT_DESCRIPTION,
				LocalQaBootstrapService.PRODUCT_DESCRIPTION,
				LocalQaBootstrapService.PRODUCT_PET_TYPE,
				null,
				LocalQaBootstrapService.PRODUCT_DISPLAY_STATUS);
	}

	private long subscriptionCount(Long memberId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM subscriptions WHERE member_id = ?",
				Long.class,
				memberId);
	}

	private Category activeCategory() {
		String suffix = UUID.randomUUID().toString();
		return categoryRepository.saveAndFlush(new Category("qa-bootstrap-" + suffix, "qa-bootstrap-" + suffix, 0, true));
	}

	private String runtimeQaEmail() {
		return LocalQaBootstrapService.QA_EMAIL_LOCAL_PART + "@" + UUID.randomUUID() + ".example";
	}
}
