package com.pawcycle.backend.catalog.engagement.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.infra.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductEngagementApiIntegrationTests {
    private final WebApplicationContext applicationContext;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final SkuRepository skus;
    private final JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private long productId;
    private long skuId;

    @Autowired
    ProductEngagementApiIntegrationTests(WebApplicationContext applicationContext, CategoryRepository categories,
            ProductRepository products, SkuRepository skus, JdbcTemplate jdbc) {
        this.applicationContext = applicationContext;
        this.categories = categories;
        this.products = products;
        this.skus = skus;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
        jdbc.update("INSERT IGNORE INTO members(id,email,password_hash,role) VALUES (1,?,?, 'ADMIN'),(2,?,?, 'USER')",
                "mvp4-admin-" + UUID.randomUUID() + "@example.test", "fixture", "mvp4-user-" + UUID.randomUUID() + "@example.test", "fixture");
        Category category = categories.saveAndFlush(new Category("MVP4 " + UUID.randomUUID(), "mvp4-" + UUID.randomUUID(), 0, true));
        Product product = products.saveAndFlush(new Product(category, "MVP4 product", "short", "description", "DOG", null, "PUBLIC"));
        Sku sku = skus.saveAndFlush(new Sku(product, "MVP4-" + UUID.randomUUID(), "2kg", new BigDecimal("1000.00"), true, 1,
                com.pawcycle.backend.catalog.sku.domain.SkuStatus.ACTIVE));
        productId = product.getId();
        skuId = sku.getId();
        jdbc.update("INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,created_at) VALUES (?,?, 'ONE_TIME','PAID',?,?,?, ?,?)",
                "MVP4-" + UUID.randomUUID(), 2L, new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), Timestamp.from(Instant.now()));
        long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount) VALUES (?,?, 'FULL',?,?,?,?,?,?)",
                orderId, skuId, sku.getSkuCode(), product.getName(), sku.getName(), sku.getPrice(), 1, sku.getPrice());
        jdbc.update("INSERT INTO deliveries(order_id,status,shipped_at) VALUES (?,'SHIPPED',?)", orderId, Timestamp.from(Instant.now()));
    }

    @Test
    void reviewRequiresDeliveredPurchaseAndHiddenReviewLeavesTrustAggregateAtZero() throws Exception {
        mockMvc.perform(post("/api/products/{productId}/reviews", productId).with(user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"content\":\"좋아요\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REVIEW_PURCHASE_REQUIRED"));

        jdbc.update("UPDATE deliveries SET status='DELIVERED',delivered_at=? WHERE order_id=(SELECT order_id FROM order_items WHERE sku_id=? LIMIT 1)", Timestamp.from(Instant.now()), skuId);
        mockMvc.perform(post("/api/products/{productId}/reviews", productId).with(user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"content\":\"좋아요\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberId").doesNotExist());
        mockMvc.perform(post("/api/products/{productId}/reviews", productId).with(user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"content\":\"중복\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

        long reviewId = jdbc.queryForObject("SELECT id FROM reviews WHERE product_id=? AND member_id=2", Long.class, productId);
        mockMvc.perform(patch("/api/admin/product-reviews/{reviewId}/visibility", reviewId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/products/{productId}/reviews", productId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trust.averageRating").value(0))
                .andExpect(jsonPath("$.trust.reviewCount").value(0));
    }

    @Test
    void answeredQuestionCannotBeChangedAndAnswerNotificationIsNotDuplicated() throws Exception {
        mockMvc.perform(post("/api/products/{productId}/questions", productId).with(user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"문의합니다\"}"))
                .andExpect(status().isOk());
        long questionId = jdbc.queryForObject("SELECT id FROM product_questions WHERE product_id=? AND member_id=2", Long.class, productId);
        mockMvc.perform(put("/api/admin/product-questions/{questionId}/answer", questionId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"답변\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/product-questions/{questionId}", questionId).with(user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"수정\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_QUESTION_LOCKED"));
        mockMvc.perform(put("/api/admin/product-questions/{questionId}/answer", questionId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"수정 답변\"}"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type='PRODUCT_QUESTION_ANSWERED' AND reference_id=?", Integer.class, questionId)).isEqualTo(1);
    }

    @Test
    void adminEndpointsKeepExistingUserBoundary() throws Exception {
        mockMvc.perform(get("/api/products/{productId}/reviews/me", productId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/product-reviews")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/product-reviews").with(user())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/product-reviews").with(admin())).andExpect(status().isOk());
    }

    @Test
    void detailSectionsExposeOnlyVisibleRowsInStableOrder() throws Exception {
        mockMvc.perform(post("/api/admin/products/{productId}/detail-sections", productId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"숨김\",\"body\":\"hidden\",\"displayOrder\":0,\"visible\":false}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/products/{productId}/detail-sections", productId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"노출\",\"body\":\"plain\",\"displayOrder\":1,\"visible\":true}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/products/{productId}", productId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.detailSections.length()").value(1))
                .andExpect(jsonPath("$.detailSections[0].title").value("노출"))
                .andExpect(jsonPath("$.detailSections[0].body").value("plain"));
    }

    private RequestPostProcessor user() { return role(MemberRole.USER, 2L); }
    private RequestPostProcessor admin() { return role(MemberRole.ADMIN, 1L); }
    private RequestPostProcessor role(MemberRole role, long id) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(id, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
