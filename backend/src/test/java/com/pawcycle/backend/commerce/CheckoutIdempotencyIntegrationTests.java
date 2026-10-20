package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.checkout.application.CheckoutIdempotencyService;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.member.address.api.MemberAddressRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.commerce.checkout.application.CheckoutResult;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import com.pawcycle.backend.support.TestSkuFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class CheckoutIdempotencyIntegrationTests {
  private final CheckoutIdempotencyService checkout;
  private final WebApplicationContext webContext;
  private final ObjectMapper json;
  private final CommerceFacade commerce;
  private final JdbcTemplate jdbc;
  private final MemberRepository members;
  private final CategoryRepository categories;
  private final ProductRepository products;
  private final SkuRepository skus;
  private final PasswordEncoder passwordEncoder;
  private Member member;
  private Sku sku;
  private long addressId;

  @Autowired
  CheckoutIdempotencyIntegrationTests(
      CheckoutIdempotencyService checkout,
      WebApplicationContext webContext,
      ObjectMapper json,
      CommerceFacade commerce,
      JdbcTemplate jdbc,
      MemberRepository members,
      CategoryRepository categories,
      ProductRepository products,
      SkuRepository skus,
      PasswordEncoder passwordEncoder) {
    this.checkout = checkout;
    this.webContext = webContext;
    this.json = json;
    this.commerce = commerce;
    this.jdbc = jdbc;
    this.members = members;
    this.categories = categories;
    this.products = products;
    this.skus = skus;
    this.passwordEncoder = passwordEncoder;
  }

  @BeforeEach
  void setUp() {
    member =
        members.saveAndFlush(
            new Member(
                "checkout-idempotency-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("test-password")));
    Category category =
        categories.saveAndFlush(new Category("Checkout", "checkout-" + UUID.randomUUID(), 0, true));
    Product product =
        products.saveAndFlush(
            new Product(
                category,
                "Checkout product",
                "Purchase test",
                "Purchase test",
                "DOG",
                null,
                "PUBLIC"));
    sku =
        skus.saveAndFlush(
            TestSkuFactory.sku(product, "Checkout SKU", BigDecimal.valueOf(1500), false, 1));
    jdbc.update(
        "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES"
            + " (?,5,0,0)",
        sku.getId());
    addressId =
        commerce.createAddress(
            member.getId(),
            new MemberAddressRequest("집", "보호자", "010-0000-0000", "06236", "서울시 강남구", null));
  }

  @Test
  void httpCheckoutAndReplayPreservePaymentPricingAndProviderGate() throws Exception {
    commerce.addCartItem(member.getId(), sku.getId(), 1);
    String key = "checkout-http-boundary-" + UUID.randomUUID();
    var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    var auth = new UsernamePasswordAuthenticationToken(
        new AuthenticatedMemberPrincipal(member.getId()), null,
        List.of(new SimpleGrantedAuthority("ROLE_USER")));
    String payload = "{\"addressId\":" + addressId + ",\"cartVersion\":1}";
    String first = mvc.perform(post("/api/checkout")
            .with(authentication(auth)).with(csrf()).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var wire = json.readTree(first);
    assertThat(wire.size()).isEqualTo(8);
    assertThat(wire.get("orderId").asLong()).isPositive();
    assertThat(wire.get("paymentId").asLong()).isPositive();
    assertThat(wire.get("orderNumber").asText()).startsWith("O-");
    assertThat(wire.get("providerOrderId").asText()).isNotBlank();
    assertThat(wire.get("orderName").asText()).isEqualTo("Checkout product");
    assertThat(wire.get("amount").decimalValue()).isEqualByComparingTo("1500");
    assertThat(wire.get("tossTestEnabled").isBoolean()).isTrue();
    var pricing = wire.get("pricing");
    assertThat(pricing.size()).isEqualTo(6);
    assertThat(pricing.get("originalAmount").decimalValue()).isEqualByComparingTo("1500");
    assertThat(pricing.get("subtotalAmount").decimalValue()).isEqualByComparingTo("1500");
    assertThat(pricing.get("discountAmount").decimalValue()).isEqualByComparingTo("0");
    assertThat(pricing.get("shippingFee").decimalValue()).isEqualByComparingTo("0");
    assertThat(pricing.get("finalAmount").decimalValue()).isEqualByComparingTo("1500");
    assertThat(pricing.get("paymentAmount").decimalValue()).isEqualByComparingTo("1500");
    String replay = mvc.perform(post("/api/checkout")
            .with(authentication(auth)).with(csrf()).header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(json.readTree(replay)).isEqualTo(wire);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM orders WHERE member_id=?", Integer.class, member.getId())).isEqualTo(1);
  }

  @Test
  void sameCheckoutRequestReplaysAfterCartChanges() {
    commerce.addCartItem(member.getId(), sku.getId(), 1);
    String key = "checkout-replay-" + UUID.randomUUID();
    CheckoutResult first = checkout.checkout(member.getId(), key, addressId, null, 1L);

    Sku secondSku = createSku("Replay second SKU");
    commerce.addCartItem(member.getId(), secondSku.getId(), 1);
    assertThat(cartVersion()).isEqualTo(2);

    CheckoutResult explicitReplay =
        checkout.checkout(member.getId(), key, addressId, null, 1L);
    CheckoutResult transitionReplay =
        checkout.checkout(member.getId(), key, addressId, null, null);
    assertThat(explicitReplay.orderId()).isEqualTo(first.orderId());
    assertThat(transitionReplay.orderId()).isEqualTo(first.orderId());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE member_id=?", Integer.class, member.getId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT available_quantity FROM inventories WHERE sku_id=?",
                Integer.class,
                sku.getId()))
        .isEqualTo(4);

    assertThatThrownBy(() -> checkout.checkout(member.getId(), key, addressId, null, 2L))
        .isInstanceOf(CommerceException.class)
        .satisfies(
            error ->
                assertThat(((CommerceException) error).code())
                    .isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
  }

  @Test
  void sameCheckoutRequestReplaysAfterSuccessfulPaymentConsumesCart() {
    commerce.addCartItem(member.getId(), sku.getId(), 1);
    String key = "checkout-paid-replay-" + UUID.randomUUID();
    CheckoutResult first = checkout.checkout(member.getId(), key, addressId, null, 1L);
    commerce.confirm(
        member.getId(),
        "payment-key",
        (String) first.providerOrderId(),
        new BigDecimal(first.amount().toString()));
    assertThat(cartVersion()).isEqualTo(2);

    CheckoutResult replay = checkout.checkout(member.getId(), key, addressId, null, 1L);
    CheckoutResult transitionReplay =
        checkout.checkout(member.getId(), key, addressId, null, null);
    assertThat(replay.orderId()).isEqualTo(first.orderId());
    assertThat(transitionReplay.orderId()).isEqualTo(first.orderId());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE member_id=?", Integer.class, member.getId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT available_quantity FROM inventories WHERE sku_id=?",
                Integer.class,
                sku.getId()))
        .isEqualTo(4);
  }

  @Test
  void omittedCartVersionStillRejectsChangedRequestIdentity() {
    commerce.addCartItem(member.getId(), sku.getId(), 1);
    String key = "checkout-address-conflict-" + UUID.randomUUID();
    checkout.checkout(member.getId(), key, addressId, null, null);
    long secondAddress =
        commerce.createAddress(
            member.getId(),
            new MemberAddressRequest("회사", "보호자", "010-0000-0000", "06237", "서울시 서초구", null));

    assertThatThrownBy(() -> checkout.checkout(member.getId(), key, secondAddress, null, null))
        .isInstanceOf(CommerceException.class)
        .satisfies(
            error ->
                assertThat(((CommerceException) error).code())
                    .isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE member_id=?", Integer.class, member.getId()))
        .isEqualTo(1);
  }

  @Test
  void legacyCheckoutWithoutStoredCartVersionFailsClosed() {
    commerce.addCartItem(member.getId(), sku.getId(), 1);
    String key = "checkout-legacy-version-" + UUID.randomUUID();
    CheckoutResult first = checkout.checkout(member.getId(), key, addressId, null, 1L);
    assertThat(
            jdbc.update(
                "UPDATE checkout_idempotency_results SET request_cart_version=NULL WHERE"
                    + " member_id=? AND idempotency_key=?",
                member.getId(),
                key))
        .isEqualTo(1);

    assertThatThrownBy(() -> checkout.checkout(member.getId(), key, addressId, null, 1L))
        .isInstanceOf(CommerceException.class)
        .satisfies(
            error ->
                assertThat(((CommerceException) error).code())
                    .isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id=?", Integer.class, first.orderId()))
        .isEqualTo(1);
  }

  @Test
  void concurrentSameMemberAndKeySerializesAndReplaysOneCheckout() throws Exception {
    commerce.addCartItem(member.getId(), sku.getId(), 1);
    String key = "checkout-concurrent-replay-" + UUID.randomUUID();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<CheckoutResult> first = executor.submit(() -> checkoutTogether(key, ready, start));
      Future<CheckoutResult> second = executor.submit(() -> checkoutTogether(key, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      CheckoutResult firstResponse = first.get(15, TimeUnit.SECONDS);
      CheckoutResult secondResponse = second.get(15, TimeUnit.SECONDS);

      assertThat(firstResponse.orderId()).isEqualTo(secondResponse.orderId());
      assertThat(firstResponse.paymentId()).isEqualTo(secondResponse.paymentId());
    }

    assertThat(count("orders", member.getId())).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments payment JOIN orders commerce_order ON"
                    + " commerce_order.id=payment.order_id WHERE commerce_order.member_id=?",
                Integer.class,
                member.getId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM checkout_idempotency_results WHERE member_id=? AND"
                    + " idempotency_key=?",
                Integer.class,
                member.getId(),
                key))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements movement JOIN payments payment ON"
                    + " payment.id=movement.payment_id JOIN orders commerce_order ON"
                    + " commerce_order.id=payment.order_id WHERE commerce_order.member_id=?"
                    + " AND movement.sku_id=? AND movement.type='RESERVE'",
                Integer.class,
                member.getId(),
                sku.getId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT available_quantity FROM inventories WHERE sku_id=?",
                Integer.class,
                sku.getId()))
        .isEqualTo(4);
  }

  @Test
  void concurrentDifferentMembersCheckoutIndependentSkusWithoutDeadlock() throws Exception {
    List<CheckoutFixture> fixtures =
        List.of(createCheckoutFixture(), createCheckoutFixture(), createCheckoutFixture(), createCheckoutFixture());
    CountDownLatch ready = new CountDownLatch(fixtures.size());
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(fixtures.size())) {
      List<Future<CheckoutResult>> results = new ArrayList<>();
      for (CheckoutFixture fixture : fixtures) {
        results.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Checkout concurrency start timed out.");
                  }
                  return checkout.checkout(
                      fixture.member().getId(), fixture.key(), fixture.addressId(), null, 1L);
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<CheckoutResult> result : results) {
        assertThat(result.get(15, TimeUnit.SECONDS)).isNotNull();
      }
    }

    for (CheckoutFixture fixture : fixtures) {
      assertThat(count("orders", fixture.member().getId())).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM payments payment JOIN orders commerce_order ON"
                      + " commerce_order.id=payment.order_id WHERE commerce_order.member_id=?",
                  Integer.class,
                  fixture.member().getId()))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM checkout_idempotency_results WHERE member_id=? AND"
                      + " idempotency_key=?",
                  Integer.class,
                  fixture.member().getId(),
                  fixture.key()))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT reserved_quantity FROM inventories WHERE sku_id=?",
                  Integer.class,
                  fixture.sku().getId()))
          .isEqualTo(1);
    }
  }

  @Test
  void concurrentDifferentMembersReserveOneSharedSkuWithoutInventoryConflicts() throws Exception {
    int requestCount = 8;
    jdbc.update("UPDATE inventories SET available_quantity=? WHERE sku_id=?", requestCount, sku.getId());
    List<CheckoutFixture> fixtures = new ArrayList<>();
    for (int index = 0; index < requestCount; index++) fixtures.add(createCheckoutFixture(sku));
    CountDownLatch ready = new CountDownLatch(fixtures.size());
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(fixtures.size())) {
      List<Future<CheckoutResult>> results = new ArrayList<>();
      for (CheckoutFixture fixture : fixtures) {
        results.add(executor.submit(() -> checkoutTogether(fixture, ready, start)));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<CheckoutResult> result : results) {
        assertThat(result.get(20, TimeUnit.SECONDS)).isNotNull();
      }
    }

    for (CheckoutFixture fixture : fixtures) {
      assertThat(count("orders", fixture.member().getId())).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM payments payment JOIN orders commerce_order ON"
                      + " commerce_order.id=payment.order_id WHERE commerce_order.member_id=?"
                      + " AND payment.status='READY'",
                  Integer.class,
                  fixture.member().getId()))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM checkout_idempotency_results WHERE member_id=? AND"
                      + " idempotency_key=?",
                  Integer.class,
                  fixture.member().getId(),
                  fixture.key()))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM inventory_movements movement JOIN payments payment ON"
                      + " payment.id=movement.payment_id JOIN orders commerce_order ON"
                      + " commerce_order.id=payment.order_id WHERE commerce_order.member_id=?"
                      + " AND movement.sku_id=? AND movement.type='RESERVE'",
                  Integer.class,
                  fixture.member().getId(),
                  sku.getId()))
          .isEqualTo(1);
    }
    assertThat(
            jdbc.queryForMap(
                "SELECT available_quantity,reserved_quantity,version FROM inventories WHERE sku_id=?",
                sku.getId()))
        .containsEntry("available_quantity", 0)
        .containsEntry("reserved_quantity", requestCount)
        .containsEntry("version", (long) requestCount);
  }

  private CheckoutResult checkoutTogether(
      String key, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Checkout concurrency start timed out.");
    }
    return checkout.checkout(member.getId(), key, addressId, null, 1L);
  }

  private CheckoutResult checkoutTogether(
      CheckoutFixture fixture, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Checkout concurrency start timed out.");
    }
    return checkout.checkout(
        fixture.member().getId(), fixture.key(), fixture.addressId(), null, 1L);
  }

  private CheckoutFixture createCheckoutFixture() {
    Member checkoutMember =
        members.saveAndFlush(
            new Member(
                "checkout-concurrent-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("test-password")));
    Category checkoutCategory =
        categories.saveAndFlush(new Category("Checkout", "checkout-" + UUID.randomUUID(), 0, true));
    Product checkoutProduct =
        products.saveAndFlush(
            new Product(
                checkoutCategory,
                "Independent checkout product",
                "Purchase test",
                "Purchase test",
                "DOG",
                null,
                "PUBLIC"));
    Sku checkoutSku =
        skus.saveAndFlush(
            TestSkuFactory.sku(checkoutProduct, "Independent checkout SKU", BigDecimal.valueOf(1500), false, 1));
    jdbc.update(
        "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,5,0,0)",
        checkoutSku.getId());
    long checkoutAddress =
        commerce.createAddress(
            checkoutMember.getId(),
            new MemberAddressRequest("집", "보호자", "010-0000-0000", "06236", "서울시 강남구", null));
    commerce.addCartItem(checkoutMember.getId(), checkoutSku.getId(), 1);
    return new CheckoutFixture(
        checkoutMember, checkoutSku, checkoutAddress, "checkout-independent-" + UUID.randomUUID());
  }

  private CheckoutFixture createCheckoutFixture(Sku sharedSku) {
    Member checkoutMember =
        members.saveAndFlush(
            new Member(
                "checkout-shared-sku-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("test-password")));
    long checkoutAddress =
        commerce.createAddress(
            checkoutMember.getId(),
            new MemberAddressRequest("집", "보호자", "010-0000-0000", "06236", "서울시 강남구", null));
    commerce.addCartItem(checkoutMember.getId(), sharedSku.getId(), 1);
    return new CheckoutFixture(
        checkoutMember, sharedSku, checkoutAddress, "checkout-shared-sku-" + UUID.randomUUID());
  }

  private int count(String table, long memberId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE member_id=?", Integer.class, memberId);
  }

  private record CheckoutFixture(Member member, Sku sku, long addressId, String key) {}

  private long cartVersion() {
    return ((Number) commerce.cart(member.getId()).get("version")).longValue();
  }

  private Sku createSku(String name) {
    Category category =
        categories.saveAndFlush(
            new Category("Checkout extra", "checkout-extra-" + UUID.randomUUID(), 1, true));
    Product product =
        products.saveAndFlush(
            new Product(
                category,
                name + " product",
                "Purchase test",
                "Purchase test",
                "DOG",
                null,
                "PUBLIC"));
    Sku created =
        skus.saveAndFlush(TestSkuFactory.sku(product, name, BigDecimal.valueOf(1500), false, 1));
    jdbc.update(
        "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES"
            + " (?,5,0,0)",
        created.getId());
    return created;
  }
}
