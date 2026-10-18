package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.brand.domain.Brand;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.cart.api.CartResponse;
import com.pawcycle.backend.commerce.cart.application.CartApplicationService;
import com.pawcycle.backend.commerce.cart.model.CartItemView;
import com.pawcycle.backend.commerce.wishlist.api.WishlistResponse;
import com.pawcycle.backend.commerce.wishlist.application.WishlistApplicationService;
import com.pawcycle.backend.commerce.wishlist.domain.WishlistItemEntity;
import com.pawcycle.backend.commerce.wishlist.domain.WishlistItemId;
import com.pawcycle.backend.commerce.wishlist.model.WishlistItemView;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
class ShoppingStateResponseMaterializationIntegrationTests {
  private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 18, 14, 59, 59, 123456000);
  @Autowired private CartApplicationService cart;
  @Autowired private WishlistApplicationService wishlist;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private EntityManagerFactory emf;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private ObjectMapper json;
  @Autowired private WebApplicationContext web;
  private long memberId, otherId, emptyId, categoryId, brandId, first, second, third, firstSku, secondSku;
  private MockMvc http;

  @BeforeEach
  void setUp() {
    http = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      memberId = fixtures.member();
      otherId = fixtures.member();
      emptyId = fixtures.member();
      Category category = fixtures.category(true);
      Brand brand = fixtures.brand(true);
      categoryId = category.getId();
      brandId = brand.getId();
      Product a = fixtures.product(category, brand, "DOG", "PUBLIC");
      Product b = fixtures.product(category, brand, "DOG", "PUBLIC");
      Product c = fixtures.product(category, brand, "DOG", "PUBLIC");
      first = a.getId(); second = b.getId(); third = c.getId();
      var skuA = new Sku(a, "SHOPPING-A-" + first, "A", new BigDecimal("123.45"), false, 0, SkuStatus.ACTIVE);
      var skuB = new Sku(b, "SHOPPING-B-" + second, "B", new BigDecimal("0.10"), false, 0, SkuStatus.ACTIVE);
      entities.persist(skuA); entities.persist(skuB); entities.flush();
      firstSku = skuA.getId(); secondSku = skuB.getId();
      jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,5,0,0),(?,0,0,0)", firstSku, secondSku);
      cart.add(memberId, secondSku, 3);
      cart.add(memberId, firstSku, 2);
      // Use a single JPA binding path so the pair is a real DATETIME(6) tie in both JVM zones.
      entities.persist(new WishlistItemEntity(new WishlistItemId(memberId, first), CREATED));
      entities.persist(new WishlistItemEntity(new WishlistItemId(memberId, second), CREATED));
      entities.persist(new WishlistItemEntity(new WishlistItemId(memberId, third), CREATED.minusNanos(1000)));
      entities.persist(new WishlistItemEntity(new WishlistItemId(otherId, third), CREATED.plusSeconds(1)));
      entities.flush();
    });
  }

  @AfterEach
  void cleanUp() {
    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      jdbc.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE member_id IN (?,?,?))", memberId, otherId, emptyId);
      jdbc.update("DELETE FROM carts WHERE member_id IN (?,?,?)", memberId, otherId, emptyId);
      jdbc.update("DELETE FROM wishlist_items WHERE member_id IN (?,?,?)", memberId, otherId, emptyId);
      jdbc.update("DELETE FROM inventories WHERE sku_id IN (?,?)", firstSku, secondSku);
      jdbc.update("DELETE FROM skus WHERE id IN (?,?)", firstSku, secondSku);
      jdbc.update("DELETE FROM products WHERE id IN (?,?,?)", first, second, third);
      jdbc.update("DELETE FROM brands WHERE id=?", brandId);
      jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
      jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", memberId, otherId, emptyId);
    });
  }

  @Test
  void cartAndMissingCartKeepTwoReadStatementsPricesVersionSkuOrderAndDetachedConversion() throws Exception {
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    var result = cart.get(memberId);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    assertThat(result.cart().items()).extracting(CartItemView::skuId).containsExactly(firstSku, secondSku);
    assertThat(result.cart().version()).isEqualTo(2);
    assertThat(result.cart().items()).extracting(CartItemView::purchasable).containsExactly(true, false);
    assertThat(result.cart().items()).extracting(CartItemView::availableQuantity).containsExactly(5, 0);
    assertThat(result.pricing().originalAmount()).isEqualTo(new BigDecimal("247.20"));
    assertThat(result.pricing().subtotalAmount()).isEqualTo(result.pricing().originalAmount());
    assertThat(result.pricing().finalAmount()).isEqualTo(result.pricing().originalAmount());
    assertThat(result.pricing().paymentAmount()).isEqualTo(result.pricing().originalAmount());
    assertThat(result.pricing().discountAmount()).isEqualTo(BigDecimal.ZERO);
    assertThat(result.pricing().shippingFee()).isEqualTo(BigDecimal.ZERO);
    unbound();
    statistics.clear();
    var expected = FrozenShoppingStateResponses.cart(result.cart());
    var actual = CartResponse.from(result);
    assertThat(actual).isEqualTo(expected);
    assertThat(json.writeValueAsString(actual)).isEqualTo(json.writeValueAsString(expected));
    assertThat(statistics.getPrepareStatementCount()).isZero();
    assertThat(statistics.getEntityLoadCount()).isZero();
    statistics.clear();
    var empty = cart.get(emptyId);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    assertThat(empty.cart().items()).isEmpty();
    assertThat(empty.cart().version()).isZero();
    assertThat(empty.pricing().paymentAmount()).isEqualTo(BigDecimal.ZERO);
    statistics.clear();
    assertThat(CartResponse.from(empty)).isEqualTo(FrozenShoppingStateResponses.cart(empty.cart()));
    assertThat(statistics.getPrepareStatementCount()).isZero();
    assertThat(statistics.getEntityLoadCount()).isZero();
    assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE member_id=?", Long.class, memberId)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE member_id=?", Integer.class, emptyId)).isZero();
  }

  @Test
  void wishlistKeepsOneReadUtcTimestampMicrosecondsTieOrderAndMemberScopeWithoutPostReturnSql() throws Exception {
    assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT created_at) FROM wishlist_items WHERE member_id=? AND product_id IN (?,?)", Integer.class, memberId, first, second)).isEqualTo(1);
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    var items = wishlist.list(memberId);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    assertThat(items).extracting(WishlistItemView::productId).containsExactly(second, first, third);
    assertThat(items.getFirst().createdAt()).isExactlyInstanceOf(Timestamp.class)
        .isEqualTo(Timestamp.from(CREATED.toInstant(ZoneOffset.UTC)));
    assertThat(items.getFirst().createdAt().getNanos()).isEqualTo(123456000);
    assertThat(items.getLast().createdAt().getNanos()).isEqualTo(123455000);
    assertThatThrownBy(() -> items.clear()).isInstanceOf(UnsupportedOperationException.class);
    unbound();
    statistics.clear();
    var expected = FrozenShoppingStateResponses.wishlist(items);
    var actual = WishlistResponse.from(items);
    assertThat(actual).isEqualTo(expected);
    assertThat(actual.items().getFirst().createdAt()).isSameAs(items.getFirst().createdAt());
    assertThat(json.writeValueAsString(actual)).isEqualTo(json.writeValueAsString(expected));
    assertThat(statistics.getPrepareStatementCount()).isZero();
    assertThat(statistics.getEntityLoadCount()).isZero();
    assertThat(wishlist.list(otherId)).extracting(WishlistItemView::productId).containsExactly(third);
    assertThat(wishlist.list(emptyId)).isEmpty();
  }

  @Test
  void httpReadsKeepFrozenJsonMemberScopeStatusAuthenticationAndCsrf() throws Exception {
    var expectedCart = FrozenShoppingStateResponses.cart(cart.get(memberId).cart());
    var expectedWishlist = FrozenShoppingStateResponses.wishlist(wishlist.list(memberId));
    var cartBody = http.perform(get("/api/cart").with(as(memberId))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var wishlistBody = http.perform(get("/api/wishlist").with(as(memberId))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(cartBody).isEqualTo(json.writeValueAsString(expectedCart));
    assertThat(wishlistBody).isEqualTo(json.writeValueAsString(expectedWishlist));
    http.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    http.perform(get("/api/wishlist")).andExpect(status().isUnauthorized());
    http.perform(post("/api/cart/items").with(as(memberId)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"skuId\":" + firstSku + ",\"quantity\":1}")).andExpect(status().isForbidden());
    http.perform(post("/api/wishlist/" + third).with(as(memberId))).andExpect(status().isForbidden());
    var emptyCart = http.perform(get("/api/cart").with(as(emptyId))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var emptyWishlist = http.perform(get("/api/wishlist").with(as(emptyId))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(json.readTree(emptyCart).get("items").size()).isZero();
    assertThat(json.readTree(emptyCart).get("version").asLong()).isZero();
    assertThat(json.readTree(emptyWishlist).get("items").size()).isZero();
    assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE member_id=?", Long.class, memberId)).isEqualTo(2);
  }

  @Test
  void returnedSnapshotsStayMaterializedAfterLaterCommittedCartAndWishlistWrites() throws Exception {
    var cartBefore = cart.get(memberId);
    var wishlistBefore = wishlist.list(memberId);
    cart.update(memberId, firstSku, 1);
    wishlist.remove(memberId, second);
    assertThat(cart.get(memberId).cart().version()).isEqualTo(3);
    assertThat(wishlist.list(memberId)).hasSize(2);
    unbound();
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    assertThat(CartResponse.from(cartBefore).version()).isEqualTo(2);
    assertThat(CartResponse.from(cartBefore).items().getFirst().quantity()).isEqualTo(2);
    assertThat(CartResponse.from(cartBefore)).isEqualTo(FrozenShoppingStateResponses.cart(cartBefore.cart()));
    assertThat(WishlistResponse.from(wishlistBefore).items()).hasSize(3);
    assertThat(json.writeValueAsString(WishlistResponse.from(wishlistBefore)))
        .isEqualTo(json.writeValueAsString(FrozenShoppingStateResponses.wishlist(wishlistBefore)));
    assertThat(statistics.getPrepareStatementCount()).isZero();
    assertThat(statistics.getEntityLoadCount()).isZero();
  }

  private void unbound() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(TransactionSynchronizationManager.hasResource(emf)).isFalse();
  }

  private RequestPostProcessor as(long id) {
    return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(id, MemberRole.USER),
        null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }
}
