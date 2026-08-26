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

@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class CheckoutIdempotencyIntegrationTests {
    private final CheckoutIdempotencyService checkout;
    private final CommerceService commerce;
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
            CommerceService commerce,
            JdbcTemplate jdbc,
            MemberRepository members,
            CategoryRepository categories,
            ProductRepository products,
            SkuRepository skus,
            PasswordEncoder passwordEncoder) {
        this.checkout = checkout;
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
        member = members.saveAndFlush(new Member(
                "checkout-idempotency-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("test-password")));
        Category category = categories.saveAndFlush(new Category(
                "Checkout", "checkout-" + UUID.randomUUID(), 0, true));
        Product product = products.saveAndFlush(new Product(
                category, "Checkout product", "Purchase test", "Purchase test", "DOG", null, "PUBLIC"));
        sku = skus.saveAndFlush(TestSkuFactory.sku(product, "Checkout SKU", BigDecimal.valueOf(1500), false, 1));
        jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,5,0,0)", sku.getId());
        addressId = commerce.createAddress(member.getId(), Map.of(
                "name", "집",
                "recipientName", "보호자",
                "recipientPhone", "010-0000-0000",
                "postalCode", "06236",
                "addressLine1", "서울시 강남구"));
    }

    @Test
    void sameCheckoutRequestReplaysAfterCartChanges() {
        commerce.addCartItem(member.getId(), sku.getId(), 1);
        String key = "checkout-replay-" + UUID.randomUUID();
        Map<String, Object> first = checkout.checkout(member.getId(), key, addressId, null, 1L);

        Sku secondSku = createSku("Replay second SKU");
        commerce.addCartItem(member.getId(), secondSku.getId(), 1);
        assertThat(cartVersion()).isEqualTo(2);

        Map<String, Object> explicitReplay = checkout.checkout(member.getId(), key, addressId, null, 1L);
        Map<String, Object> transitionReplay = checkout.checkout(member.getId(), key, addressId, null, null);
        assertThat(explicitReplay.get("orderId")).isEqualTo(first.get("orderId"));
        assertThat(transitionReplay.get("orderId")).isEqualTo(first.get("orderId"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?", Integer.class, member.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT available_quantity FROM inventories WHERE sku_id=?", Integer.class, sku.getId())).isEqualTo(4);

        assertThatThrownBy(() -> checkout.checkout(member.getId(), key, addressId, null, 2L))
                .isInstanceOf(CommerceException.class)
                .satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void sameCheckoutRequestReplaysAfterSuccessfulPaymentConsumesCart() {
        commerce.addCartItem(member.getId(), sku.getId(), 1);
        String key = "checkout-paid-replay-" + UUID.randomUUID();
        Map<String, Object> first = checkout.checkout(member.getId(), key, addressId, null, 1L);
        commerce.confirm(
                member.getId(),
                "payment-key",
                (String) first.get("providerOrderId"),
                new BigDecimal(first.get("amount").toString()));
        assertThat(cartVersion()).isEqualTo(2);

        Map<String, Object> replay = checkout.checkout(member.getId(), key, addressId, null, 1L);
        Map<String, Object> transitionReplay = checkout.checkout(member.getId(), key, addressId, null, null);
        assertThat(replay.get("orderId")).isEqualTo(first.get("orderId"));
        assertThat(transitionReplay.get("orderId")).isEqualTo(first.get("orderId"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?", Integer.class, member.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT available_quantity FROM inventories WHERE sku_id=?", Integer.class, sku.getId())).isEqualTo(4);
    }

    @Test
    void omittedCartVersionStillRejectsChangedRequestIdentity() {
        commerce.addCartItem(member.getId(), sku.getId(), 1);
        String key = "checkout-address-conflict-" + UUID.randomUUID();
        checkout.checkout(member.getId(), key, addressId, null, null);
        long secondAddress = commerce.createAddress(member.getId(), Map.of(
                "name", "회사",
                "recipientName", "보호자",
                "recipientPhone", "010-0000-0000",
                "postalCode", "06237",
                "addressLine1", "서울시 서초구"));

        assertThatThrownBy(() -> checkout.checkout(member.getId(), key, secondAddress, null, null))
                .isInstanceOf(CommerceException.class)
                .satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?", Integer.class, member.getId())).isEqualTo(1);
    }

    @Test
    void legacyCheckoutWithoutStoredCartVersionFailsClosed() {
        commerce.addCartItem(member.getId(), sku.getId(), 1);
        String key = "checkout-legacy-version-" + UUID.randomUUID();
        Map<String, Object> first = checkout.checkout(member.getId(), key, addressId, null, 1L);
        assertThat(jdbc.update("UPDATE checkout_idempotency_results SET request_cart_version=NULL WHERE member_id=? AND idempotency_key=?", member.getId(), key)).isEqualTo(1);

        assertThatThrownBy(() -> checkout.checkout(member.getId(), key, addressId, null, 1L))
                .isInstanceOf(CommerceException.class)
                .satisfies(error -> assertThat(((CommerceException) error).code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id=?", Integer.class, first.get("orderId"))).isEqualTo(1);
    }

    private long cartVersion() {
        return ((Number) commerce.cart(member.getId()).get("version")).longValue();
    }

    private Sku createSku(String name) {
        Category category = categories.saveAndFlush(new Category(
                "Checkout extra", "checkout-extra-" + UUID.randomUUID(), 1, true));
        Product product = products.saveAndFlush(new Product(
                category, name + " product", "Purchase test", "Purchase test", "DOG", null, "PUBLIC"));
        Sku created = skus.saveAndFlush(TestSkuFactory.sku(product, name, BigDecimal.valueOf(1500), false, 1));
        jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,5,0,0)", created.getId());
        return created;
    }
}
