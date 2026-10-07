package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.commerce.cart.persistence.CartQueryRepository;
import com.pawcycle.backend.commerce.order.application.OrderApplicationService;
import com.pawcycle.backend.commerce.order.persistence.OrderPersistenceAdapter;
import com.pawcycle.backend.commerce.order.persistence.OrderView;
import com.pawcycle.backend.commerce.order.persistence.QuickReorderPersistenceAdapter;
import com.pawcycle.backend.commerce.wishlist.persistence.WishlistQueryRepository;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import com.pawcycle.backend.support.TestSkuFactory;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class CustomerOrderReadModelIntegrationTests {
  @Autowired private OrderPersistenceAdapter orders;
  @Autowired private OrderApplicationService service;
  @Autowired private CartQueryRepository carts;
  @Autowired private WishlistQueryRepository wishlists;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MemberRepository members;
  @Autowired private CategoryRepository categories;
  @Autowired private ProductRepository products;
  @Autowired private SkuRepository skus;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private Clock clock;
  @Autowired private ObjectMapper json;
  @Autowired private WebApplicationContext context;
  private LegacyOrderReadReference legacy;
  private MockMvc http;
  private long memberId;
  private long otherId;
  private Product product;
  private Sku firstSku;
  private Sku secondSku;

  @BeforeEach
  void setUp() {
    memberId = members.saveAndFlush(new Member("read-" + UUID.randomUUID() + "@example.test", "unused-fixture")).getId();
    otherId = members.saveAndFlush(new Member("read-" + UUID.randomUUID() + "@example.test", "unused-fixture")).getId();
    Category category = categories.saveAndFlush(new Category("read fixture", "read-" + UUID.randomUUID(), 0, true));
    product = products.saveAndFlush(new Product(category, "Live product", "read fixture", "read fixture", "DOG", null, "PUBLIC"));
    firstSku = skus.saveAndFlush(TestSkuFactory.sku(product, "Live SKU", new BigDecimal("9999.00"), false, 1));
    secondSku = skus.saveAndFlush(TestSkuFactory.sku(product, "Other live SKU", new BigDecimal("8888.00"), false, 2));
    legacy = new LegacyOrderReadReference(jdbc);
    http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void listKeepsMemberIsolationDescendingIdsAndEverySummaryValue() {
    long oldest = order(memberId, "ONE_TIME", "CREATED", false);
    order(otherId, "ONE_TIME", "PAID", true);
    long newest = order(memberId, "SUBSCRIPTION", "PAID", true);
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(orders.findOrders(memberId)).isEqualTo(legacy.findOrders(memberId));
    assertThat(orders.findOrders(memberId)).extracting(OrderPersistenceAdapter.Summary::orderId)
        .containsExactly(newest, oldest);
    assertThat(orders.findOrders(memberId).getFirst().paidAt()).isNotNull();
    assertThat(orders.findOrders(memberId).getLast().paidAt()).isNull();
    assertThat(orders.findOrders(Long.MAX_VALUE)).isEmpty();
  }

  @Test
  void missingForeignAndChildlessOrdersKeepNullEmptyAndHttpNotFound() throws Exception {
    long id = order(memberId, "ONE_TIME", "CREATED", false);
    assertThat(orders.findOrder(otherId, id)).isNull();
    assertThat(orders.findOrder(memberId, Long.MAX_VALUE)).isNull();
    OrderView view = orders.findOrder(memberId, id);
    assertThat(view).isEqualTo(legacy.findOrder(memberId, id));
    assertThat(view.items()).isEmpty();
    assertThat(view.refunds()).isEmpty();
    assertThat(view.payment()).isNull();
    assertThat(view.delivery()).isNull();
    assertThat(view.cancellation()).isNull();
    assertThat(view.returnRequest()).isNull();
    assertThat(view.paidAt()).isNull();
    http.perform(get("/api/orders/{id}", id).with(member(otherId)))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    http.perform(get("/api/orders/{id}", Long.MAX_VALUE).with(member(memberId)))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    assertApiEqualsLegacy(id);
  }

  @Test
  void fullProjectionPreservesSnapshotsAttemptsNullableRestockAndTimestampPrecision() throws Exception {
    long id = fullOrder();
    OrderView view = orders.findOrder(memberId, id);
    assertThat(view).isEqualTo(legacy.findOrder(memberId, id));
    assertThat(view.items()).extracting(OrderView.Item::skuId).containsExactly(secondSku.getId(), firstSku.getId());
    assertThat(view.items().getFirst().productNameSnapshot()).isEqualTo("Historic product");
    assertThat(view.items().getFirst().unitPrice()).isEqualByComparingTo("123.45");
    assertThat(view.items().getLast().snapshotQuality()).isEqualTo("LEGACY_PARTIAL");
    assertThat(view.items().getLast().unitPrice()).isNull();
    assertThat(view.payment().attemptNo()).isEqualTo(3);
    assertThat(view.payment().providerStatus()).isEqualTo("attempt-3");
    assertThat(view.refunds()).extracting(OrderView.Refund::attemptNo).containsExactly(1, 2, 3);
    assertThat(view.refunds()).extracting(OrderView.Refund::reconciliationAttempts).containsExactly(2, 4, 6);
    assertThat(view.returnRequest().restock()).isNull();
    assertThat(view.createdAt().getNanos()).isEqualTo(123456000);
    assertApiEqualsLegacy(id);
    for (boolean restock : List.of(true, false)) {
      jdbc.update("UPDATE order_returns SET restock=? WHERE order_id=?", restock, id);
      assertThat(orders.findOrder(memberId, id)).isEqualTo(legacy.findOrder(memberId, id));
      assertThat(orders.findOrder(memberId, id).returnRequest().restock()).isEqualTo(restock);
    }
  }

  @Test
  void deliveryStatesKeepEveryOptionalTimestampAndFailureField() {
    long id = order(memberId, "ONE_TIME", "PAID", true);
    jdbc.update("INSERT INTO deliveries(order_id,status) VALUES (?,'PREPARING')", id);
    for (String state : List.of("PREPARING", "SHIPPED", "DELIVERED", "FAILED", "CANCELLED")) {
      jdbc.update("""
          UPDATE deliveries SET status=?,carrier_code='fixture-carrier',tracking_number='fixture-tracking',
            failure_reason=?,shipped_at=?,delivered_at=?,failed_at=?,cancelled_at=? WHERE order_id=?
          """, state, state.equals("FAILED") ? "fixture failure" : null,
          List.of("SHIPPED", "DELIVERED", "FAILED").contains(state) ? stamp() : null,
          state.equals("DELIVERED") ? stamp() : null, state.equals("FAILED") ? stamp() : null,
          state.equals("CANCELLED") ? stamp() : null, id);
      assertThat(orders.findOrder(memberId, id)).isEqualTo(legacy.findOrder(memberId, id));
    }
  }

  @Test
  void apiShapeAndAvailableActionsKeepExistingResponseLogic() throws Exception {
    long id = order(memberId, "ONE_TIME", "PAID", true);
    jdbc.update("INSERT INTO deliveries(order_id,status) VALUES (?,'PREPARING')", id);
    assertThat(service.order(memberId, id).availableActions()).containsExactly("REQUEST_CANCELLATION");
    assertApiEqualsLegacy(id);
    Timestamp delivered = Timestamp.from(clock.instant().minus(1, ChronoUnit.DAYS));
    jdbc.update("UPDATE deliveries SET status='DELIVERED',shipped_at=?,delivered_at=? WHERE order_id=?", delivered, delivered, id);
    assertThat(service.order(memberId, id).availableActions()).containsExactly("REQUEST_RETURN");
    assertApiEqualsLegacy(id);
    jdbc.update("INSERT INTO order_returns(order_id,status,reason,requested_at) VALUES (?,'REQUESTED','fixture return',?)", id, stamp());
    assertThat(service.order(memberId, id).availableActions()).isEmpty();
    assertApiEqualsLegacy(id);
    String response = http.perform(get("/api/orders").with(member(memberId))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    OrderPersistenceAdapter reference = mock(OrderPersistenceAdapter.class);
    when(reference.findOrders(memberId)).thenReturn(legacy.findOrders(memberId));
    assertThat(json.readTree(response)).isEqualTo(json.readTree(json.writeValueAsString(referenceService(reference).orders(memberId))));
  }

  @Test
  void existingCartAndWishlistTypedReadsKeepOwnershipOrderingAndDoNotMutateCartVersion() {
    jdbc.update("INSERT INTO carts(member_id,version,created_at,updated_at) VALUES (?,7,?,?)", memberId, stamp(), stamp());
    jdbc.update("INSERT INTO carts(member_id,version,created_at,updated_at) VALUES (?,3,?,?)", otherId, stamp(), stamp());
    jdbc.update("INSERT INTO cart_items(cart_id,sku_id,quantity) SELECT id,?,2 FROM carts WHERE member_id=?", firstSku.getId(), memberId);
    jdbc.update("INSERT INTO cart_items(cart_id,sku_id,quantity) SELECT id,?,1 FROM carts WHERE member_id=?", secondSku.getId(), otherId);
    jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,5,0,0)", firstSku.getId());
    jdbc.update("INSERT INTO wishlist_items(member_id,product_id,created_at) VALUES (?,?,?)", memberId, product.getId(), stamp());
    Product otherProduct = products.saveAndFlush(new Product(categories.findById(product.getCategory().getId()).orElseThrow(),
        "Wishlist second", "fixture", "fixture", "DOG", null, "PUBLIC"));
    jdbc.update("INSERT INTO wishlist_items(member_id,product_id,created_at) VALUES (?,?,?)", memberId, otherProduct.getId(), stamp());
    jdbc.update("INSERT INTO wishlist_items(member_id,product_id,created_at) VALUES (?,?,?)", otherId, product.getId(), stamp());
    assertThat(carts.find(memberId).items()).singleElement().satisfies(item -> {
      assertThat(item.skuId()).isEqualTo(firstSku.getId());
      assertThat(item.quantity()).isEqualTo(2);
      assertThat(item.productName()).isEqualTo("Live product");
      assertThat(item.lineAmount()).isEqualByComparingTo("19998.00");
      assertThat(item.purchasable()).isTrue();
    });
    assertThat(carts.find(memberId).version()).isEqualTo(7);
    assertThat(carts.find(otherId).items().getFirst().skuId()).isEqualTo(secondSku.getId());
    assertThat(wishlists.findByMemberId(memberId)).extracting(item -> item.productId())
        .containsExactly(otherProduct.getId(), product.getId());
    assertThat(wishlists.findByMemberId(otherId)).hasSize(1);
    assertThat(carts.find(Long.MAX_VALUE).items()).isEmpty();
    assertThat(wishlists.findByMemberId(Long.MAX_VALUE)).isEmpty();
    assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE member_id=?", Long.class, memberId)).isEqualTo(7);
  }

  private void assertApiEqualsLegacy(long id) throws Exception {
    OrderPersistenceAdapter reference = mock(OrderPersistenceAdapter.class);
    when(reference.findOrder(memberId, id)).thenReturn(legacy.findOrder(memberId, id));
    String response = http.perform(get("/api/orders/{id}", id).with(member(memberId)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(json.readTree(response)).isEqualTo(json.readTree(json.writeValueAsString(referenceService(reference).order(memberId, id))));
  }

  private OrderApplicationService referenceService(OrderPersistenceAdapter reference) {
    return new OrderApplicationService(reference, mock(QuickReorderPersistenceAdapter.class), transactions, clock, 7);
  }

  private RequestPostProcessor member(long id) {
    return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(id), null,
        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  private static Timestamp stamp() { return Timestamp.valueOf("2026-09-18 12:34:56.123456"); }

  private long order(long owner, String source, String status, boolean paid) {
    String number = "READ-" + UUID.randomUUID();
    jdbc.update("""
        INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,
          payment_amount,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,paid_at)
        VALUES (?,?,?, ?,1234.56,34.56,100,1300,'Historic recipient','fixture-phone','fixture-postal',
          'Historic address','Historic detail',?,?)
        """, number, owner, source, status, stamp(), paid ? stamp() : null);
    return jdbc.queryForObject("SELECT id FROM orders WHERE order_number=?", Long.class, number);
  }

  private long fullOrder() {
    long id = order(memberId, "ONE_TIME", "PAID", true);
    jdbc.update("""
        INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,
          sku_name_snapshot,unit_price,quantity,line_amount) VALUES (?,?,'FULL','historic-code',
          'Historic product','Historic SKU',123.45,2,246.90)
        """, id, secondSku.getId());
    jdbc.update("INSERT INTO order_items(order_id,sku_id,snapshot_quality,quantity) VALUES (?,?,'LEGACY_PARTIAL',1)", id, firstSku.getId());
    for (int attempt : List.of(3, 1, 2)) {
      jdbc.update("""
          INSERT INTO payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,
            attempt_no,provider_status,requested_at,created_at) VALUES (?,'NORMAL','TOSS','READY',1300,?,?,?,?,?,?)
          """, id, "READ-P-" + UUID.randomUUID(), "READ-P-" + UUID.randomUUID(), attempt, "attempt-" + attempt, stamp(), stamp());
    }
    jdbc.update("INSERT INTO deliveries(order_id,status,shipped_at,failed_at,failure_reason) VALUES (?,'FAILED',?,?,'fixture failure')", id, stamp(), stamp());
    jdbc.update("INSERT INTO order_cancellations(order_id,status,reason,requested_at,completed_at) VALUES (?,'COMPLETED','fixture cancellation',?,?)", id, stamp(), stamp());
    jdbc.update("""
        INSERT INTO order_returns(order_id,status,reason,rejection_reason,requested_at,decided_at,received_at,completed_at)
        VALUES (?,'COMPLETED','fixture return','fixture rejection',?,?,?,?)
        """, id, stamp(), stamp(), stamp(), stamp());
    long cancellation = jdbc.queryForObject("SELECT id FROM order_cancellations WHERE order_id=?", Long.class, id);
    for (int attempt : List.of(3, 1, 2)) {
      jdbc.update("""
          INSERT INTO refunds(order_id,source,cancellation_id,status,amount,provider,idempotency_key,
            attempt_no,reconciliation_attempts,requested_at) VALUES (?,'CANCELLATION',?,'READY',1300,'TOSS',?,?,?,?)
          """, id, cancellation, "READ-R-" + UUID.randomUUID(), attempt, attempt * 2, stamp());
    }
    return id;
  }
}
