package com.pawcycle.backend.subscription.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(RepeatCommerceServiceIntegrationTests.FixedClockConfiguration.class)
class RepeatCommerceServiceIntegrationTests {
	@Autowired private RepeatCommerceService repeat;
	@Autowired private MemberRepository members;
	@Autowired private CategoryRepository categories;
	@Autowired private ProductRepository products;
	@Autowired private SkuRepository skus;
	@Autowired private JdbcTemplate jdbc;
	private Member member;
	private Product product;
	private Sku firstSku;
	private Sku secondSku;

	@BeforeEach
	void setUp() {
		member = members.saveAndFlush(new Member("repeat-" + UUID.randomUUID() + "@example.test", "fixture-password"));
		Category category = categories.saveAndFlush(new Category("repeat-" + UUID.randomUUID(), "repeat-" + UUID.randomUUID(), 0, true));
		product = new Product(category, "Repeat product", "fixture", null, "DOG", null);
		product.transitionTo(com.pawcycle.backend.catalog.product.domain.ProductStatus.PUBLIC);
		product = products.saveAndFlush(product);
		firstSku = skus.saveAndFlush(new Sku(product, "REPEAT-A-" + UUID.randomUUID(), "small", new BigDecimal("1000.00"), true, 1, com.pawcycle.backend.catalog.sku.domain.SkuStatus.ACTIVE));
		secondSku = skus.saveAndFlush(new Sku(product, "REPEAT-B-" + UUID.randomUUID(), "large", new BigDecimal("1200.00"), true, 2, com.pawcycle.backend.catalog.sku.domain.SkuStatus.ACTIVE));
	}

	@Test
	void multipleSkusInOneOrderProduceOneProductPurchaseDate() {
		oneTimeOrder(LocalDate.of(2026, 7, 31), List.of(firstSku, secondSku));
		oneTimeOrder(LocalDate.of(2026, 8, 10), List.of(firstSku));
		oneTimeOrder(LocalDate.of(2026, 8, 20), List.of(firstSku));

		Map<String, Object> result = repeat.reorderTiming(member.getId());

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
		assertThat(items).singleElement().satisfies(item -> {
			assertThat(item.get("purchaseCount")).isEqualTo(3);
			assertThat(item.get("lastPurchasedDate")).isEqualTo(LocalDate.of(2026, 8, 20));
			assertThat(item.get("expectedReorderDate")).isEqualTo(LocalDate.of(2026, 8, 30));
		});
	}

	private void oneTimeOrder(LocalDate paidDate, List<Sku> orderSkus) {
		String suffix = UUID.randomUUID().toString();
		Timestamp time = Timestamp.valueOf(paidDate.atStartOfDay());
		jdbc.update("INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,created_at,paid_at) VALUES (?,?,'ONE_TIME','PAID',1000,0,0,1000,?,?)", "REPEAT-" + suffix, member.getId(), time, time);
		long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,attempt_no,requested_at,approved_at,created_at) VALUES (?,'NORMAL','TOSS','SUCCEEDED',1000,?,?,1,?,?,?)", orderId, "provider-" + suffix, "idempotency-" + suffix, time, time, time);
		for (Sku sku : orderSkus) jdbc.update("INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount) VALUES (?,?,'FULL',?,?,?,?,1,?)", orderId, sku.getId(), sku.getSkuCode(), product.getName(), sku.getName(), sku.getPrice(), sku.getPrice());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {
		@Bean
		@Primary
		Clock fixedRepeatClock() { return Clock.fixed(Instant.parse("2026-08-28T00:30:00Z"), ZoneOffset.UTC); }
	}
}
