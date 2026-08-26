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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
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
		assertThat(((Number) cartItem.get("availableQuantity")).intValue()).isEqualTo(5);
		assertThat(asBoolean(cartItem.get("purchasable"))).isTrue();
		assertAmount(cartItem.get("lineAmount"), BigDecimal.valueOf(3000));
		assertAmount(cartPricing.get("originalAmount"), BigDecimal.valueOf(3000));
		assertAmount(cartPricing.get("paymentAmount"), BigDecimal.valueOf(3000));

		String idempotencyKey = "checkout-" + UUID.randomUUID();
		Map<String, Object> checkout = commerce.checkout(member.getId(), idempotencyKey, addressId, null);
		Map<String, Object> pricing = (Map<String, Object>) checkout.get("pricing");
		assertAmount(pricing.get("originalAmount"), BigDecimal.valueOf(3000));
		assertAmount(pricing.get("discountAmount"), BigDecimal.ZERO);
		assertAmount(pricing.get("shippingFee"), BigDecimal.ZERO);
		assertAmount(pricing.get("finalAmount"), BigDecimal.valueOf(3000));
		assertThat(jdbc.queryForObject("SELECT available_quantity FROM inventories WHERE sku_id=?", Integer.class, sku.getId())).isEqualTo(3);

		Map<String, Object> replayed = commerce.checkout(member.getId(), idempotencyKey, addressId, null);
		Map<String, Object> replayedPricing = (Map<String, Object>) replayed.get("pricing");
		assertThat(replayed.get("orderId")).isEqualTo(checkout.get("orderId"));
		assertAmount(replayedPricing.get("originalAmount"), BigDecimal.valueOf(3000));
		assertAmount(replayedPricing.get("subtotalAmount"), BigDecimal.valueOf(3000));
		assertAmount(replayedPricing.get("discountAmount"), BigDecimal.ZERO);
		assertAmount(replayedPricing.get("shippingFee"), BigDecimal.ZERO);
		assertAmount(replayedPricing.get("finalAmount"), BigDecimal.valueOf(3000));
		assertAmount(replayedPricing.get("paymentAmount"), BigDecimal.valueOf(3000));
		assertThat(jdbc.queryForObject("SELECT available_quantity FROM inventories WHERE sku_id=?", Integer.class, sku.getId())).isEqualTo(3);

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

	private static boolean asBoolean(Object value) {
		return value instanceof Boolean booleanValue ? booleanValue : value instanceof Number numberValue && numberValue.intValue() != 0;
	}

	private static void assertAmount(Object actual, BigDecimal expected) {
		assertThat(new BigDecimal(actual.toString()).compareTo(expected)).isZero();
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

	@Test
	void successfulConfirmReturnsOrderIdAndReplayDoesNotConsumeCartTwice() {
		commerce.addCartItem(member.getId(), sku.getId(), 1);
		Map<String, Object> checkout = commerce.checkout(member.getId(), "payment-" + UUID.randomUUID(), addressId, null);
		String providerOrderId = (String) checkout.get("providerOrderId");
		Map<String, Object> confirmed = commerce.confirm(member.getId(), "payment-key", providerOrderId, new BigDecimal(checkout.get("amount").toString()));

		assertThat(confirmed).containsEntry("status", "SUCCEEDED").containsKey("orderId");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items item JOIN carts cart ON cart.id=item.cart_id WHERE cart.member_id=? AND item.sku_id=?", Integer.class, member.getId(), sku.getId())).isZero();
		Map<String, Object> replayed = commerce.confirm(member.getId(), "payment-key", providerOrderId, new BigDecimal(checkout.get("amount").toString()));
		assertThat(replayed).containsEntry("status", "SUCCEEDED").containsEntry("orderId", confirmed.get("orderId"));
		assertThat(jdbc.queryForObject("SELECT available_quantity FROM inventories WHERE sku_id=?", Integer.class, sku.getId())).isEqualTo(4);
	}

	@Test
	void confirmRejectsWrongMemberAndAmountWithoutChangingPayment() {
		commerce.addCartItem(member.getId(), sku.getId(), 1);
		Map<String, Object> checkout = commerce.checkout(member.getId(), "ownership-payment-" + UUID.randomUUID(), addressId, null);
		Member other = memberRepository.saveAndFlush(new Member("payment-other-" + UUID.randomUUID() + "@example.test", passwordEncoder.encode("test-password")));
		String providerOrderId = (String) checkout.get("providerOrderId");

		assertThatThrownBy(() -> commerce.confirm(other.getId(), "payment-key", providerOrderId, new BigDecimal(checkout.get("amount").toString())))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("PAYMENT_FORBIDDEN"));
		assertThatThrownBy(() -> commerce.confirm(member.getId(), "payment-key", providerOrderId, BigDecimal.valueOf(1)))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("PAYMENT_CONFIRM_CONFLICT"));
		assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE provider_order_id=?", String.class, providerOrderId)).isEqualTo("READY");
	}

	@Test
	void failedConfirmReleasesReservationAndMarksOrderFailed() {
		commerce.addCartItem(member.getId(), sku.getId(), 1);
		Map<String, Object> checkout = commerce.checkout(member.getId(), "failed-payment-" + UUID.randomUUID(), addressId, null);
		String providerOrderId = (String) checkout.get("providerOrderId");

		Map<String, Object> failed = commerce.confirm(member.getId(), "fail_test", providerOrderId, new BigDecimal(checkout.get("amount").toString()));

		assertThat(failed).containsEntry("status", "FAILED").containsKey("orderId");
		assertThat(jdbc.queryForObject("SELECT orders.status FROM orders JOIN payments ON payments.order_id=orders.id WHERE payments.provider_order_id=?", String.class, providerOrderId)).isEqualTo("PAYMENT_FAILED");
		assertThat(jdbc.queryForObject("SELECT available_quantity FROM inventories WHERE sku_id=?", Integer.class, sku.getId())).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM inventories WHERE sku_id=?", Integer.class, sku.getId())).isZero();
	}

	@Test
	void unknownConfirmKeepsReservationForReconciliation() {
		commerce.addCartItem(member.getId(), sku.getId(), 1);
		Map<String, Object> checkout = commerce.checkout(member.getId(), "unknown-payment-" + UUID.randomUUID(), addressId, null);
		String providerOrderId = (String) checkout.get("providerOrderId");

		Map<String, Object> unknown = commerce.confirm(member.getId(), "unknown_test", providerOrderId, new BigDecimal(checkout.get("amount").toString()));

		assertThat(unknown).containsEntry("status", "UNKNOWN").containsKey("orderId");
		assertThat(jdbc.queryForObject("SELECT orders.status FROM orders JOIN payments ON payments.order_id=orders.id WHERE payments.provider_order_id=?", String.class, providerOrderId)).isEqualTo("PAYMENT_PENDING");
		assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE provider_order_id=?", String.class, providerOrderId)).isEqualTo("UNKNOWN");
		assertThat(jdbc.queryForObject("SELECT available_quantity FROM inventories WHERE sku_id=?", Integer.class, sku.getId())).isEqualTo(4);
		assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM inventories WHERE sku_id=?", Integer.class, sku.getId())).isEqualTo(1);
	}

	@Test
	void cartVersionChangesOnlyWhenCartContentChanges() {
		assertThat(cartVersion()).isZero();
		commerce.addCartItem(member.getId(), sku.getId(), 1);
		assertThat(cartVersion()).isEqualTo(1);
		commerce.updateCartItem(member.getId(), sku.getId(), 1);
		assertThat(cartVersion()).isEqualTo(1);
		commerce.updateCartItem(member.getId(), sku.getId(), 2);
		assertThat(cartVersion()).isEqualTo(2);
		commerce.deleteCartItem(member.getId(), sku.getId());
		assertThat(cartVersion()).isEqualTo(3);
		commerce.deleteCartItem(member.getId(), sku.getId());
		assertThat(cartVersion()).isEqualTo(3);
	}

	@Test
	void checkoutRejectsStaleCartVersionAndFingerprintConflictsWithoutSideEffects() {
		commerce.addCartItem(member.getId(), sku.getId(), 1);
		assertThatThrownBy(() -> commerce.checkout(member.getId(), "stale-" + UUID.randomUUID(), addressId, null, 0L))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("CART_CHANGED"));

		String key = "fingerprint-" + UUID.randomUUID();
		Map<String, Object> first = commerce.checkout(member.getId(), key, addressId, null, 1L);
		long secondAddress = commerce.createAddress(member.getId(), Map.of(
				"name", "회사", "recipientName", "보호자", "recipientPhone", "010-0000-0000", "postalCode", "06237", "addressLine1", "서울시 서초구"));
		assertThatThrownBy(() -> commerce.checkout(member.getId(), key, secondAddress, null, 1L))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));

		Sku secondSku = createSku("Fingerprint SKU", 1500, 5);
		commerce.addCartItem(member.getId(), secondSku.getId(), 1);
		assertThatThrownBy(() -> commerce.checkout(member.getId(), key, addressId, null, 2L))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?", Integer.class, member.getId())).isEqualTo(1);
		assertThat(first).containsKey("orderId");

		String couponKey = "coupon-fingerprint-" + UUID.randomUUID();
		commerce.checkout(member.getId(), couponKey, addressId, null, 2L);
		jdbc.update("INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,valid_from,valid_until,active) VALUES ('fingerprint coupon','FIXED_AMOUNT',100,0,CURRENT_TIMESTAMP(6),DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 DAY),true)");
		long couponId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO member_coupons(member_id,coupon_id,status,issued_at) VALUES (?,?,'AVAILABLE',CURRENT_TIMESTAMP(6))", member.getId(), couponId);
		long memberCouponId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		assertThatThrownBy(() -> commerce.checkout(member.getId(), couponKey, addressId, memberCouponId, 2L))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
	}

	@Test
	void legacyCheckoutFingerprintFailsClosed() {
		commerce.addCartItem(member.getId(), sku.getId(), 1);
		String key = "legacy-fingerprint-" + UUID.randomUUID();
		Map<String, Object> first = commerce.checkout(member.getId(), key, addressId, null, 1L);
		assertThat(jdbc.update("UPDATE checkout_idempotency_results SET request_fingerprint=NULL WHERE member_id=? AND idempotency_key=?", member.getId(), key)).isEqualTo(1);
		assertThatThrownBy(() -> commerce.checkout(member.getId(), key, addressId, null, 1L))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id=?", Integer.class, first.get("orderId"))).isEqualTo(1);
	}

	@Test
	void quickReorderAddsPurchasableItemsSkipsUnavailableItemsAndReplaysWithoutMutation() {
		Sku inactiveSku = createSku("Inactive SKU", 900, 5);
		jdbc.update("UPDATE skus SET status='INACTIVE' WHERE id=?", inactiveSku.getId());
		long sourceOrderId = insertSourceOrder(member.getId(), List.of(
				new OrderLine(sku.getId(), 2, BigDecimal.valueOf(1500)),
				new OrderLine(inactiveSku.getId(), 1, BigDecimal.valueOf(900))));
		int movementsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class);

		Map<String, Object> result = commerce.reorder(member.getId(), sourceOrderId, "reorder-" + UUID.randomUUID());
		@SuppressWarnings("unchecked") Map<String, Object> added = ((List<Map<String, Object>>) result.get("addedItems")).getFirst();
		@SuppressWarnings("unchecked") Map<String, Object> skipped = ((List<Map<String, Object>>) result.get("skippedItems")).getFirst();
		assertThat(added).containsEntry("skuId", sku.getId()).containsEntry("quantity", 2);
		assertThat(skipped).containsEntry("skuId", inactiveSku.getId()).containsEntry("reason", "SKU_NOT_PURCHASABLE");
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items item JOIN carts cart ON cart.id=item.cart_id WHERE cart.member_id=? AND item.sku_id=?", Integer.class, member.getId(), sku.getId())).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Integer.class)).isEqualTo(movementsBefore);

		Map<String, Object> replay = commerce.reorder(member.getId(), sourceOrderId, "reorder-replay");
		Map<String, Object> replayAgain = commerce.reorder(member.getId(), sourceOrderId, "reorder-replay");
		assertThat(replayAgain.get("addedItems")).isEqualTo(replay.get("addedItems"));
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items item JOIN carts cart ON cart.id=item.cart_id WHERE cart.member_id=? AND item.sku_id=?", Integer.class, member.getId(), sku.getId())).isEqualTo(4);
	}

	@Test
	void quickReorderEnforcesOwnershipAndKeySourceConflict() {
		long sourceOrderId = insertSourceOrder(member.getId(), List.of(new OrderLine(sku.getId(), 1, BigDecimal.valueOf(1500))));
		Member other = memberRepository.saveAndFlush(new Member("reorder-other-" + UUID.randomUUID() + "@example.test", passwordEncoder.encode("test-password")));
		long otherOrderId = insertSourceOrder(other.getId(), List.of(new OrderLine(sku.getId(), 1, BigDecimal.valueOf(1500))));
		assertThatThrownBy(() -> commerce.reorder(other.getId(), sourceOrderId, "ownership-reorder"))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("ORDER_NOT_FOUND"));
		commerce.reorder(member.getId(), sourceOrderId, "source-conflict");
		assertThatThrownBy(() -> commerce.reorder(member.getId(), otherOrderId, "source-conflict"))
				.isInstanceOf(CommerceException.class)
				.satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
	}

	private long cartVersion() {
		return ((Number) commerce.cart(member.getId()).get("version")).longValue();
	}

	private Sku createSku(String name, int price, int availableQuantity) {
		Category category = categoryRepository.saveAndFlush(new Category("사료", "commerce-extra-" + UUID.randomUUID(), 1, true));
		Product product = productRepository.saveAndFlush(new Product(category, name + " product", "Purchase test", "Purchase test", "DOG", null, "PUBLIC"));
		Sku created = skuRepository.saveAndFlush(TestSkuFactory.sku(product, name, BigDecimal.valueOf(price), false, 1));
		jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,?,0,0)", created.getId(), availableQuantity);
		return created;
	}

	private long insertSourceOrder(long ownerId, List<OrderLine> lines) {
		BigDecimal total = lines.stream().map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
		jdbc.update("INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,created_at) VALUES (?,?,'ONE_TIME','PAID',?,0,0,?,CURRENT_TIMESTAMP(6))", "REORDER-" + UUID.randomUUID(), ownerId, total, total);
		long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		for (OrderLine line : lines) {
			jdbc.update("INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount) SELECT ?,sku.id,'FULL',sku.sku_code,product.name,sku.name,sku.price,?,sku.price*? FROM skus sku JOIN products product ON product.id=sku.product_id WHERE sku.id=?", orderId, line.quantity(), line.quantity(), line.skuId());
		}
		return orderId;
	}

	private record OrderLine(long skuId, int quantity, BigDecimal price) { }
}
