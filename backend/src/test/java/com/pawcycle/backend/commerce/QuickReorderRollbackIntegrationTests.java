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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class QuickReorderRollbackIntegrationTests {

	private final CommerceService commerce;
	private final JdbcTemplate jdbc;
	private final MemberRepository members;
	private final CategoryRepository categories;
	private final ProductRepository products;
	private final SkuRepository skus;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	QuickReorderRollbackIntegrationTests(
			CommerceService commerce,
			JdbcTemplate jdbc,
			MemberRepository members,
			CategoryRepository categories,
			ProductRepository products,
			SkuRepository skus,
			PasswordEncoder passwordEncoder) {
		this.commerce = commerce;
		this.jdbc = jdbc;
		this.members = members;
		this.categories = categories;
		this.products = products;
		this.skus = skus;
		this.passwordEncoder = passwordEncoder;
	}

	@Test
	void unexpectedPersistenceFailureRollsBackAllCartMutationsAndIdempotencyResult() {
		Member member = members.saveAndFlush(new Member(
				"reorder-rollback-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode("test-password")));
		Sku firstSku = createSku("Rollback first", 1500);
		Sku overflowSku = createSku("Rollback overflow", 1700);

		commerce.addCartItem(member.getId(), firstSku.getId(), 1);
		commerce.addCartItem(member.getId(), overflowSku.getId(), 1);
		jdbc.update("""
			UPDATE cart_items item
			JOIN carts cart ON cart.id=item.cart_id
			SET item.quantity=?
			WHERE cart.member_id=? AND item.sku_id=?
			""", Integer.MAX_VALUE, member.getId(), overflowSku.getId());

		long cartVersionBefore = cartVersion(member.getId());
		long sourceOrderId = insertSourceOrder(member.getId(), firstSku, overflowSku);
		String idempotencyKey = "rollback-" + UUID.randomUUID();

		assertThatThrownBy(() -> commerce.reorder(member.getId(), sourceOrderId, idempotencyKey))
				.isInstanceOf(RuntimeException.class);

		assertThat(cartQuantity(member.getId(), firstSku.getId())).isEqualTo(1);
		assertThat(cartQuantity(member.getId(), overflowSku.getId())).isEqualTo(Integer.MAX_VALUE);
		assertThat(cartVersion(member.getId())).isEqualTo(cartVersionBefore);
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM quick_reorder_idempotency_results WHERE member_id=? AND idempotency_key=?",
				Integer.class,
				member.getId(), idempotencyKey)).isZero();
	}

	private Sku createSku(String name, int price) {
		Category category = categories.saveAndFlush(new Category(
				name + " category", "rollback-" + UUID.randomUUID(), 0, true));
		Product product = products.saveAndFlush(new Product(
				category, name + " product", "rollback", "rollback", "DOG", null, "PUBLIC"));
		Sku sku = skus.saveAndFlush(TestSkuFactory.sku(
				product, name + " SKU", BigDecimal.valueOf(price), false, 1));
		jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,10,0,0)", sku.getId());
		return sku;
	}

	private long insertSourceOrder(long memberId, Sku firstSku, Sku overflowSku) {
		BigDecimal total = firstSku.getPrice().add(overflowSku.getPrice());
		jdbc.update("""
			INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,created_at)
			VALUES (?,?,'ONE_TIME','PAID',?,0,0,?,CURRENT_TIMESTAMP(6))
			""", "ROLLBACK-" + UUID.randomUUID(), memberId, total, total);
		long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		insertOrderItem(orderId, firstSku);
		insertOrderItem(orderId, overflowSku);
		return orderId;
	}

	private void insertOrderItem(long orderId, Sku sku) {
		jdbc.update("""
			INSERT INTO order_items(
				order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount)
			SELECT ?,sku.id,'FULL',sku.sku_code,product.name,sku.name,sku.price,1,sku.price
			FROM skus sku JOIN products product ON product.id=sku.product_id
			WHERE sku.id=?
			""", orderId, sku.getId());
	}

	private int cartQuantity(long memberId, long skuId) {
		return jdbc.queryForObject("""
			SELECT item.quantity
			FROM cart_items item JOIN carts cart ON cart.id=item.cart_id
			WHERE cart.member_id=? AND item.sku_id=?
			""", Integer.class, memberId, skuId);
	}

	private long cartVersion(long memberId) {
		return jdbc.queryForObject("SELECT version FROM carts WHERE member_id=?", Long.class, memberId);
	}
}
