package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.member.address.api.MemberAddressRequest;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import com.pawcycle.backend.support.TestSkuFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Characterization of stock reservation under concurrent checkout on real MySQL.
 *
 * <p>These tests record current behavior (CONSISTENCY-001, known-risks R6). They do not change
 * production code. Each checkout commits in its own transaction, so the test cleans up its own rows.
 */
@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class CheckoutInventoryConcurrencyIntegrationTests {
  private static final int TIMEOUT_SECONDS = 60;

  @Autowired private CommerceFacade commerce;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MemberRepository memberRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private SkuRepository skuRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private final List<Long> memberIds = new ArrayList<>();
  private final List<Long> skuIds = new ArrayList<>();
  private Long productId;
  private Long categoryId;

  @Test
  void lastUnitsOfOneSkuAreNeverOversoldUnderConcurrentCheckout() throws Exception {
    long skuId = sku("Shared SKU", 10);
    List<Buyer> buyers = new ArrayList<>();
    for (int i = 0; i < 30; i++) buyers.add(buyer(List.of(skuId)));

    Outcome outcome = checkoutConcurrently(buyers);

    assertThat(outcome.unexpected()).as("unexpected failures").isEmpty();
    assertThat(outcome.succeeded()).isEqualTo(10);
    assertThat(outcome.stockRejected()).isEqualTo(20);
    assertInventory(skuId, 0, 10, 10);
  }

  @Test
  void multiSkuCartsInOppositeOrderDoNotDeadlock() throws Exception {
    long first = sku("Multi SKU A", 100);
    long second = sku("Multi SKU B", 100);
    List<Buyer> buyers = new ArrayList<>();
    for (int i = 0; i < 10; i++) buyers.add(buyer(List.of(first, second)));
    for (int i = 0; i < 10; i++) buyers.add(buyer(List.of(second, first)));

    Outcome outcome = checkoutConcurrently(buyers);

    assertThat(outcome.unexpected()).as("deadlock or other unexpected failures").isEmpty();
    assertThat(outcome.succeeded()).isEqualTo(20);
    assertInventory(first, 80, 20, 20);
    assertInventory(second, 80, 20, 20);
  }

  private Outcome checkoutConcurrently(List<Buyer> buyers) throws Exception {
    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger stockRejected = new AtomicInteger();
    Map<String, Integer> unexpected = new ConcurrentHashMap<>();
    CountDownLatch ready = new CountDownLatch(buyers.size());
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(buyers.size());
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (Buyer buyer : buyers) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  try {
                    if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                      throw new IllegalStateException("start timeout");
                    }
                    commerce.checkout(
                        buyer.memberId(), "concurrency-" + UUID.randomUUID(), buyer.addressId(), null);
                    succeeded.incrementAndGet();
                  } catch (CommerceException exception) {
                    if (List.of("INVENTORY_INSUFFICIENT", "INVENTORY_CONFLICT")
                        .contains(exception.code())) {
                      stockRejected.incrementAndGet();
                    } else {
                      unexpected.merge(exception.code(), 1, Integer::sum);
                    }
                  } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    unexpected.merge("INTERRUPTED", 1, Integer::sum);
                  } catch (RuntimeException exception) {
                    unexpected.merge(exception.getClass().getSimpleName(), 1, Integer::sum);
                  }
                  return null;
                }));
      }
      assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<?> future : futures) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
    return new Outcome(succeeded.get(), stockRejected.get(), Map.copyOf(unexpected));
  }

  private void assertInventory(long skuId, int available, int reserved, int reserveMovements) {
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT available_quantity, reserved_quantity FROM inventories WHERE sku_id=?", skuId);
    assertThat(((Number) row.get("available_quantity")).intValue()).isEqualTo(available);
    assertThat(((Number) row.get("reserved_quantity")).intValue()).isEqualTo(reserved);
    assertThat(
            jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity),0) FROM inventory_movements WHERE sku_id=? AND type='RESERVE'",
                Integer.class,
                skuId))
        .as("RESERVE movement total matches reserved quantity")
        .isEqualTo(reserveMovements);
  }

  private long sku(String name, int stock) {
    if (productId == null) {
      Category category =
          categoryRepository.saveAndFlush(
              new Category("동시성", "concurrency-" + UUID.randomUUID(), 0, true));
      categoryId = category.getId();
      Product product =
          productRepository.saveAndFlush(
              new Product(category, "Concurrency product", "test", "test", "DOG", null, "PUBLIC"));
      productId = product.getId();
    }
    Product product = productRepository.findById(productId).orElseThrow();
    Sku sku =
        skuRepository.saveAndFlush(TestSkuFactory.sku(product, name, BigDecimal.valueOf(1500), false, 1));
    jdbc.update(
        "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,?,0,0)",
        sku.getId(),
        stock);
    skuIds.add(sku.getId());
    return sku.getId();
  }

  private Buyer buyer(List<Long> cartSkuIds) {
    Member member =
        memberRepository.saveAndFlush(
            new Member(
                "concurrency-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("test-password")));
    memberIds.add(member.getId());
    long addressId =
        commerce.createAddress(
            member.getId(),
            new MemberAddressRequest("집", "보호자", "010-0000-0000", "06236", "서울시 강남구", "101호"));
    for (Long skuId : cartSkuIds) commerce.addCartItem(member.getId(), skuId, 1);
    return new Buyer(member.getId(), addressId);
  }

  @AfterEach
  void cleanUp() {
    for (Long memberId : memberIds) {
      List<Long> orders =
          jdbc.queryForList("SELECT id FROM orders WHERE member_id=?", Long.class, memberId);
      for (Long orderId : orders) {
        jdbc.update(
            "DELETE FROM inventory_movements WHERE payment_id IN (SELECT id FROM payments WHERE order_id=?)",
            orderId);
        jdbc.update("DELETE FROM checkout_idempotency_results WHERE order_id=?", orderId);
        jdbc.update("DELETE FROM payments WHERE order_id=?", orderId);
        jdbc.update("DELETE FROM order_items WHERE order_id=?", orderId);
        jdbc.update("DELETE FROM orders WHERE id=?", orderId);
      }
      jdbc.update(
          "DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE member_id=?)", memberId);
      jdbc.update("DELETE FROM carts WHERE member_id=?", memberId);
      jdbc.update("UPDATE members SET default_address_id=NULL WHERE id=?", memberId);
      jdbc.update("DELETE FROM member_addresses WHERE member_id=?", memberId);
      jdbc.update("DELETE FROM members WHERE id=?", memberId);
    }
    for (Long skuId : skuIds) {
      jdbc.update("DELETE FROM inventory_movements WHERE sku_id=?", skuId);
      jdbc.update("DELETE FROM inventories WHERE sku_id=?", skuId);
      jdbc.update("DELETE FROM skus WHERE id=?", skuId);
    }
    if (productId != null) jdbc.update("DELETE FROM products WHERE id=?", productId);
    if (categoryId != null) jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
  }

  private record Buyer(long memberId, long addressId) {}

  private record Outcome(int succeeded, int stockRejected, Map<String, Integer> unexpected) {}
}
