package com.pawcycle.backend.subscription.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class V2IdempotencyCleanupConcurrencyIntegrationTests {

	private static final long AWAIT_SECONDS = 15;

	@Autowired private V2SubscriptionService service;
	@Autowired private V2IdempotencyCleanupService cleanup;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MemberRepository members;
	@Autowired private ProductRepository products;
	@Autowired private SkuRepository skus;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager transactionManager;

	@Test
	void replayHoldingRowLockReturnsStoredResultBeforeCleanupDeletesExpiredRow() throws Exception {
		Fixture fixture = createFixture("replay-first");
		LocalDateTime initialCompletedAt = expire(fixture);
		TransactionTemplate replayTransaction = new TransactionTemplate(transactionManager);
		TransactionTemplate cleanupTransaction = new TransactionTemplate(transactionManager);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch replayLocked = new CountDownLatch(1);
		CountDownLatch cleanupStarted = new CountDownLatch(1);
		CountDownLatch cleanupFinished = new CountDownLatch(1);
		CountDownLatch allowReplayCommit = new CountDownLatch(1);
		try {
			Future<ReplayObservation> replay = executor.submit(() -> replayTransaction.execute(status -> {
				V2SubscriptionService.V2Result result = service.createSubscription(
						fixture.memberId(), fixture.key(), fixture.request());
				LocalDateTime completedAt = completedAt(fixture.memberId(), fixture.key());
				replayLocked.countDown();
				await(allowReplayCommit);
				return new ReplayObservation(result, completedAt);
			}));
			assertThat(replayLocked.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

			Future<V2IdempotencyCleanupService.CleanupResult> cleanupResult = executor.submit(() ->
					cleanupTransaction.execute(status -> {
						cleanupStarted.countDown();
						try {
							return cleanup.deleteExpired(1);
						} finally {
							cleanupFinished.countDown();
						}
					}));
			assertThat(cleanupStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			assertThat(cleanupFinished.await(250, TimeUnit.MILLISECONDS)).isFalse();

			allowReplayCommit.countDown();
			ReplayObservation observation = replay.get(AWAIT_SECONDS, TimeUnit.SECONDS);
			V2IdempotencyCleanupService.CleanupResult deleted = cleanupResult.get(AWAIT_SECONDS, TimeUnit.SECONDS);

			assertThat(observation.result().replay()).isTrue();
			assertThat(((Number) observation.result().body().get("subscriptionId")).longValue()).isEqualTo(fixture.subscriptionId());
			assertThat(observation.completedAt()).isEqualTo(initialCompletedAt);
			assertThat(deleted.creationDeleted()).isEqualTo(1);
			assertThat(deleted.commandDeleted()).isZero();
			assertThat(resultExists(fixture.memberId(), fixture.key())).isFalse();
		} finally {
			allowReplayCommit.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void cleanupCommittedFirstMakesSameKeyANewRequestWithNewCompletion() throws Exception {
		Fixture fixture = createFixture("cleanup-first");
		LocalDateTime initialCompletedAt = expire(fixture);
		TransactionTemplate cleanupTransaction = new TransactionTemplate(transactionManager);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<V2IdempotencyCleanupService.CleanupResult> cleanupResult = executor.submit(() ->
					cleanupTransaction.execute(status -> cleanup.deleteExpired(1)));
			V2IdempotencyCleanupService.CleanupResult deleted = cleanupResult.get(AWAIT_SECONDS, TimeUnit.SECONDS);
			assertThat(deleted.creationDeleted()).isEqualTo(1);
			assertThat(resultExists(fixture.memberId(), fixture.key())).isFalse();

			V2SubscriptionService.V2Result created = service.createSubscription(
					fixture.memberId(), fixture.key(), fixture.request());
			LocalDateTime newCompletedAt = completedAt(fixture.memberId(), fixture.key());

			assertThat(created.replay()).isFalse();
			assertThat(((Number) created.body().get("subscriptionId")).longValue()).isNotEqualTo(fixture.subscriptionId());
			assertThat(resultExists(fixture.memberId(), fixture.key())).isTrue();
			assertThat(newCompletedAt).isAfter(initialCompletedAt);
		} finally {
			executor.shutdownNow();
		}
	}

	private Fixture createFixture(String prefix) {
		Member member = members.saveAndFlush(new Member(
				"v2-cleanup-race-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode("test-password")));
		Product product = products.saveAndFlush(new Product(
				"V2 cleanup race product", "test", null, "DOG", null, "PUBLIC"));
		Sku sku = skus.saveAndFlush(new Sku(
				product, "v2-cleanup-race-sku-" + UUID.randomUUID(), new BigDecimal("12000.00"), true, 1));
		jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale) VALUES (?,?,true)", "DOG cleanup race plan", "DOG");
		long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,24000,false)", planId);
		long planVersionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,2)", planVersionId, sku.getId());
		jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,4)", planVersionId);
		jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", planVersionId, planId);
		long petId = ((Number) service.createPet(
				member.getId(), Map.of("name", "동시성 반려동물", "petType", "DOG")).get("petId")).longValue();
		Map<String, Object> request = Map.of(
				"petId", petId,
				"planVersionId", planVersionId,
				"deliveryCycleWeeks", 4);
		String key = prefix + "-" + UUID.randomUUID();
		V2SubscriptionService.V2Result created = service.createSubscription(member.getId(), key, request);
		return new Fixture(
				member.getId(),
				key,
				request,
				((Number) created.body().get("subscriptionId")).longValue());
	}

	private LocalDateTime expire(Fixture fixture) {
		assertThat(jdbc.update(
				"UPDATE subscription_creation_idempotency_results SET completed_at=UTC_TIMESTAMP(6)-INTERVAL 31 DAY WHERE member_id=? AND idempotency_key=?",
				fixture.memberId(),
				fixture.key())).isEqualTo(1);
		return completedAt(fixture.memberId(), fixture.key());
	}

	private LocalDateTime completedAt(long memberId, String key) {
		return jdbc.queryForObject(
				"SELECT completed_at FROM subscription_creation_idempotency_results WHERE member_id=? AND idempotency_key=?",
				LocalDateTime.class,
				memberId,
				key);
	}

	private boolean resultExists(long memberId, String key) {
		return jdbc.queryForObject(
				"SELECT COUNT(*) FROM subscription_creation_idempotency_results WHERE member_id=? AND idempotency_key=?",
				Integer.class,
				memberId,
				key) == 1;
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
				throw new AssertionError("Timed out waiting for transaction coordination");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError("Interrupted while coordinating transactions", exception);
		}
	}

	private record Fixture(long memberId, String key, Map<String, Object> request, long subscriptionId) {}

	private record ReplayObservation(V2SubscriptionService.V2Result result, LocalDateTime completedAt) {}
}
