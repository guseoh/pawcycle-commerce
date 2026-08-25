package com.pawcycle.backend.commerce;

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
import com.pawcycle.backend.support.TestSkuFactory;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommercePurchaseIntegrationTests {

	private final CommerceService commerce;
	private final JdbcTemplate jdbc;
	private final MemberRepository memberRepository;
	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;
	private final PasswordEncoder passwordEncoder;
	private Member member;
	private Sku sku;
	private long addressId;

	@Autowired
	CommercePurchaseIntegrationTests(
			CommerceService commerce,
			JdbcTemplate jdbc,
			MemberRepository memberRepository,
			CategoryRepository categoryRepository,
			ProductRepository productRepository,
			SkuRepository skuRepository,
			PasswordEncoder passwordEncoder) {
		this.commerce = commerce;
		this.jdbc = jdbc;
		this.memberRepository = memberRepository;
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@BeforeEach
	void setUp() {
		member = memberRepository.saveAndFlush(new Member(
				"commerce-purchase-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode("test-password")));
		Category category = categoryRepository.saveAndFlush(new Category("사료", "commerce-food-" + UUID.randomUUID(), 0, true));
		Product product = productRepository.saveAndFlush(new Product(category, "Commerce product", "Purchase test", "Purchase test", "DOG", null, "PUBLIC"));
		sku = skuRepository.saveAndFlush(TestSkuFactory.sku(product, "Commerce SKU", BigDecimal.valueOf(1500), false, 1));
		jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,5,0,0)", sku.getId());
		addressId = commerce.createAddress(member.getId(), Map.of(
				"name", "집",
				"recipientName", "보호자",
				"recipientPhone", "010-0000-0000",
				"postalCode", "06236",
				"addressLine1", "서울시 강남구",
				"addressLine2", "101호"));
	}

	@Test
	void cartProjectionAndCheckoutKeepServerPricingAndStockAsAuthority() {
		commerce.addCartItem(member.getId(), sku.getId(), 2);

		Map<String, Object> cart = commerce.cart(member.getId());
		Map<String, Object> cartItem = ((java.util.List<Map<String, Object>>) cart.get("items")).getFirst();
		Map<String, Object> cartPricing = (Map<String, Object>) cart.get("pricing");
		assertThat(cartItem).containsEntry("availableQuantity", 5).containsEntry("purchasable", true).containsEntry("lineAmount", BigDecimal.valueOf(3000).setScale(2));
		assertThat(cartPricing).containsEntry("originalAmount", BigDecimal.valueOf(3000).setScale(2)).containsEntry("paymentAmount", BigDecimal.valueOf(3000).setScale(2));

		Map<String, Object> checkout = commerce.checkout(member.getId(), "checkout-" + UUID.randomUUID(), addressId, null);
		Map<String, Object> pricing = (Map<String, Object>) checkout.get("pricing");
		assertThat(pricing).containsEntry("originalAmount", BigDecimal.valueOf(3000).setScale(2)).containsEntry("discountAmount", BigDecimal.ZERO.setScale(2)).containsEntry("shippingFee", BigDecimal.ZERO.setScale(2)).containsEntry("finalAmount", BigDecimal.valueOf(3000).setScale(2));
		assertThat(jdbc.queryForObject("SELECT available_quantity FROM inventories WHERE sku_id=?", Integer.class, sku.getId())).isZero();

		Map<String, Object> order = commerce.order(member.getId(), ((Number) checkout.get("orderId")).longValue());
		assertThat(order).containsKeys("items", "payment", "delivery", "recipientName", "originalAmount", "discountAmount", "shippingFee", "paymentAmount");
		assertThat(((java.util.List<Map<String, Object>>) order.get("items"))).singleElement().satisfies(item -> assertThat(item).containsEntry("quantity", 2).containsEntry("productNameSnapshot", "Commerce product"));
	}

	@Test
	void checkoutRejectsStockConflictBeforeCreatingAnOrder() {
		commerce.addCartItem(member.getId(), sku.getId(), 2);
		jdbc.update("UPDATE inventories SET available_quantity=1,version=version+1 WHERE sku_id=?", sku.getId());

		assertThatThrownBy(() -> commerce.checkout(member.getId(), "stock-conflict-" + UUID.randomUUID(), addressId, null))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isIn("INVENTORY_INSUFFICIENT", "INVENTORY_CONFLICT"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?", Integer.class, member.getId())).isZero();
	}

	@Test
	void orderProjectionDoesNotExposeAnotherMembersOrder() {
		commerce.addCartItem(member.getId(), sku.getId(), 1);
		Map<String, Object> checkout = commerce.checkout(member.getId(), "ownership-" + UUID.randomUUID(), addressId, null);
		Member other = memberRepository.saveAndFlush(new Member("commerce-other-" + UUID.randomUUID() + "@example.test", passwordEncoder.encode("test-password")));

		assertThatThrownBy(() -> commerce.order(other.getId(), ((Number) checkout.get("orderId")).longValue()))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("ORDER_NOT_FOUND"));
	}
}
