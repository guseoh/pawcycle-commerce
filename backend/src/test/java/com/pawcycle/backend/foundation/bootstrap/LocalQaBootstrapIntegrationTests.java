package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import com.pawcycle.backend.subscription.domain.Subscription;
import com.pawcycle.backend.subscription.infra.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

	private final LocalQaBootstrapService bootstrapService;
	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final PasswordEncoder passwordEncoder;
	private final JdbcTemplate jdbcTemplate;

	@Autowired
	LocalQaBootstrapIntegrationTests(
			LocalQaBootstrapService bootstrapService,
			MemberRepository memberRepository,
			ProductRepository productRepository,
			SkuRepository skuRepository,
			SubscriptionRepository subscriptionRepository,
			PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate) {
		this.bootstrapService = bootstrapService;
		this.memberRepository = memberRepository;
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
	}

	@BeforeEach
	@AfterEach
	void cleanBootstrapFixtures() {
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
		Product otherProduct = productRepository.saveAndFlush(new Product(
				OTHER_PRODUCT_PREFIX + UUID.randomUUID(),
				"비대상 상품",
				null,
				"CAT",
				null,
				"PUBLIC"));
		Sku otherSku = skuRepository.saveAndFlush(new Sku(
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
	void ambiguousFixtureRollsBackMemberCreation() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		productRepository.saveAllAndFlush(List.of(exactFixtureProduct(), exactFixtureProduct()));

		assertThatThrownBy(() -> bootstrapService.bootstrap(email, password, false))
				.isInstanceOf(LocalQaBootstrapException.class);

		assertThat(memberRepository.findByEmail(email)).isEmpty();
		assertThat(productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME)).hasSize(2);
	}

	private Product exactFixtureProduct() {
		return new Product(
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

	private String runtimeQaEmail() {
		return LocalQaBootstrapService.QA_EMAIL_LOCAL_PART + "@" + UUID.randomUUID() + ".example";
	}
}
