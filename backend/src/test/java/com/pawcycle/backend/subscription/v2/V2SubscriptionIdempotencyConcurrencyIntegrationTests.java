package com.pawcycle.backend.subscription.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class V2SubscriptionIdempotencyConcurrencyIntegrationTests {

	@Autowired private V2SubscriptionService service;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MemberRepository members;
	@Autowired private ProductRepository products;
	@Autowired private SkuRepository skus;
	@Autowired private PasswordEncoder passwordEncoder;

	@Test
	void concurrentSameCommandKeyReturnsOneSuccessAndOneReplay() throws Exception {
		Member member = members.saveAndFlush(new Member("v2-concurrent-" + UUID.randomUUID() + "@example.test", passwordEncoder.encode("test-password")));
		Product product = products.saveAndFlush(new Product("V2 concurrent product", "test", null, "DOG", null, "PUBLIC"));
		Sku sku = skus.saveAndFlush(com.pawcycle.backend.support.TestSkuFactory.sku(
				product, "v2-concurrent-sku-" + UUID.randomUUID(), new BigDecimal("12000.00"), true, 1));
		jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)", "DOG concurrent plan", "DOG");
		long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,24000,false)", planId);
		long planVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,2)", planVersionId, sku.getId());
		jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,4)", planVersionId);
		jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", planVersionId, planId);
		long petId = ((Number) service.createPet(member.getId(), Map.of("name", "동시성 반려동물", "petType", "DOG")).get("petId")).longValue();
		V2SubscriptionService.V2Result created = service.createSubscription(member.getId(), "concurrent-create", Map.of("petId", petId, "planVersionId", planVersionId, "deliveryCycleWeeks", 4));
		long subscriptionId = ((Number) created.body().get("subscriptionId")).longValue();

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<V2SubscriptionService.V2Result> first = executor.submit(() -> {
				start.await();
				return service.command(member.getId(), subscriptionId, "pause", "concurrent-pause", "\"0\"", Map.of());
			});
			Future<V2SubscriptionService.V2Result> second = executor.submit(() -> {
				start.await();
				return service.command(member.getId(), subscriptionId, "pause", "concurrent-pause", "\"0\"", Map.of());
			});
			start.countDown();

			List<V2SubscriptionService.V2Result> results = List.of(
					first.get(15, TimeUnit.SECONDS),
					second.get(15, TimeUnit.SECONDS));

			assertThat(results).extracting(V2SubscriptionService.V2Result::replay).containsExactlyInAnyOrder(false, true);
			assertThat(results).allSatisfy(result -> {
				assertThat(result.etag()).isEqualTo("\"1\"");
				assertThat(result.body()).containsEntry("status", "PAUSED");
				assertThat(((Number) result.body().get("version")).longValue()).isEqualTo(1L);
			});
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_command_history WHERE subscription_id=? AND command_type='PAUSE'", Integer.class, subscriptionId)).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}
}
