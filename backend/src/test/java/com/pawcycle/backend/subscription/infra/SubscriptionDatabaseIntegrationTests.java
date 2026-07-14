package com.pawcycle.backend.subscription.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionDatabaseIntegrationTests {

	private final JdbcTemplate jdbcTemplate;
	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;
	private final PasswordEncoder passwordEncoder;
	private Member member;
	private Sku sku;

	@Autowired
	SubscriptionDatabaseIntegrationTests(
			JdbcTemplate jdbcTemplate,
			MemberRepository memberRepository,
			ProductRepository productRepository,
			SkuRepository skuRepository,
			PasswordEncoder passwordEncoder) {
		this.jdbcTemplate = jdbcTemplate;
		this.memberRepository = memberRepository;
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@BeforeEach
	void setUp() {
		member = memberRepository.saveAndFlush(new Member(
				"subscription-db-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode(UUID.randomUUID().toString())));
		Product product = productRepository.saveAndFlush(new Product(
				"구독 DB 상품", "구독 DB 짧은 설명", null, "DOG", null, "PUBLIC"));
		sku = skuRepository.saveAndFlush(new Sku(
				product, "구독 DB SKU", new BigDecimal("19900.00"), true, 1));
	}

	@Test
	void subscriptionColumnsForeignKeysChecksAndIndexMatchApprovedContract() {
		List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
				SELECT column_name, column_type, is_nullable
				FROM information_schema.columns
				WHERE table_schema = DATABASE()
				  AND table_name = 'subscriptions'
				ORDER BY ordinal_position
				""");
		assertThat(columns).extracting(row -> row.get("COLUMN_NAME"))
				.containsExactly("id", "member_id", "sku_id", "quantity", "delivery_cycle_weeks",
						"created_date", "next_order_date");
		assertThat(columns).allSatisfy(row -> assertThat(row.get("IS_NULLABLE")).isEqualTo("NO"));

		List<String> constraints = jdbcTemplate.queryForList("""
				SELECT constraint_name
				FROM information_schema.table_constraints
				WHERE constraint_schema = DATABASE()
				  AND table_name = 'subscriptions'
				""", String.class);
		assertThat(constraints).contains(
				"PRIMARY",
				"fk_subscriptions_member",
				"fk_subscriptions_sku",
				"chk_subscriptions_quantity",
				"chk_subscriptions_delivery_cycle",
				"chk_subscriptions_date_order");

		List<String> indexColumns = jdbcTemplate.queryForList("""
				SELECT column_name
				FROM information_schema.statistics
				WHERE table_schema = DATABASE()
				  AND table_name = 'subscriptions'
				  AND index_name = 'idx_subscriptions_member_id'
				ORDER BY seq_in_index
				""", String.class);
		assertThat(indexColumns).containsExactly("member_id", "id");
	}

	@Test
	void databaseRejectsQuantityCycleDateAndForeignKeyViolations() {
		LocalDate createdDate = LocalDate.of(2026, 7, 14);
		assertRejectedInsert(member.getId(), sku.getId(), 0, 4, createdDate, createdDate.plusWeeks(4));
		assertRejectedInsert(member.getId(), sku.getId(), 11, 4, createdDate, createdDate.plusWeeks(4));
		assertRejectedInsert(member.getId(), sku.getId(), 1, 6, createdDate, createdDate.plusWeeks(6));
		assertRejectedInsert(member.getId(), sku.getId(), 1, 4, createdDate, createdDate);
		assertRejectedInsert(member.getId(), sku.getId(), 1, 4, createdDate, createdDate.minusDays(1));
		assertRejectedInsert(Long.MAX_VALUE, sku.getId(), 1, 4, createdDate, createdDate.plusWeeks(4));
		assertRejectedInsert(member.getId(), Long.MAX_VALUE, 1, 4, createdDate, createdDate.plusWeeks(4));
	}

	@Test
	void databaseAllowsLaterDateThatDoesNotMatchDeliveryCycle() {
		LocalDate createdDate = LocalDate.of(2026, 7, 14);

		assertThat(insertSubscription(
				member.getId(), sku.getId(), 1, 4, createdDate, createdDate.plusDays(1)))
				.isEqualTo(1);
	}

	private void assertRejectedInsert(
			Long memberId,
			Long skuId,
			int quantity,
			int cycle,
			LocalDate createdDate,
			LocalDate nextOrderDate) {
		assertThatThrownBy(() -> insertSubscription(
				memberId, skuId, quantity, cycle, createdDate, nextOrderDate))
				.isInstanceOf(DataAccessException.class);
	}

	private int insertSubscription(
			Long memberId,
			Long skuId,
			int quantity,
			int cycle,
			LocalDate createdDate,
			LocalDate nextOrderDate) {
		return jdbcTemplate.update("""
				INSERT INTO subscriptions
				    (member_id, sku_id, quantity, delivery_cycle_weeks, created_date, next_order_date)
				VALUES (?, ?, ?, ?, ?, ?)
				""", memberId, skuId, quantity, cycle, createdDate, nextOrderDate);
	}
}
