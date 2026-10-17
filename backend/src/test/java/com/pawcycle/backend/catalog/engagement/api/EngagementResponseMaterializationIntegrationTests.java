package com.pawcycle.backend.catalog.engagement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pawcycle.backend.catalog.engagement.application.ProductEngagementException;
import com.pawcycle.backend.catalog.engagement.application.ProductEngagementService;
import com.pawcycle.backend.catalog.engagement.application.ReviewCreateCommand;
import com.pawcycle.backend.catalog.engagement.application.ReviewPatchCommand;
import com.pawcycle.backend.catalog.engagement.application.QuestionCreateCommand;
import com.pawcycle.backend.catalog.engagement.application.QuestionPatchCommand;
import com.pawcycle.backend.catalog.engagement.model.PageView;
import com.pawcycle.backend.catalog.engagement.persistence.ProductEngagementPersistence;
import java.sql.Timestamp;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.*;
import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.notification.application.NotificationService;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
class EngagementResponseMaterializationIntegrationTests {
  private static final Instant NOW = Instant.parse("2026-10-17T14:59:59.123456Z");
  @Autowired private ProductEngagementService service;
  @Autowired private ProductEngagementPersistence persistence;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private ObjectMapper json;
  @MockitoSpyBean private NotificationService notifications;
  @MockitoSpyBean private AdminAuditService audits;
  private long adminId, memberId, otherId, categoryId, productId, skuId, orderId, reviewId, questionId;

  @BeforeEach
  void setUp() {
    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      adminId = member("admin", "ADMIN");
      memberId = member("member", "USER");
      otherId = member("other", "USER");
      jdbc.update("INSERT INTO categories(name,slug,display_order,active) VALUES ('experience-boundary','experience-boundary',0,true)");
      categoryId = lastId();
      jdbc.update("INSERT INTO products(brand_id,category_id,name,short_description,pet_type,display_status) VALUES (1,?,'experience-boundary','short','DOG','PUBLIC')", categoryId);
      productId = lastId();
      jdbc.update("INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES (?,'EXPERIENCE-BOUNDARY','sku',100,true,0,'ACTIVE')", productId);
      skuId = lastId();
      jdbc.update("INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,payment_amount,created_at) VALUES ('EXPERIENCE-BOUNDARY',?,'ONE_TIME','PAID',100,0,0,100,?)", memberId, local());
      orderId = lastId();
      jdbc.update("INSERT INTO order_items(order_id,sku_id,snapshot_quality,sku_code_snapshot,product_name_snapshot,sku_name_snapshot,unit_price,quantity,line_amount) VALUES (?,?,'FULL','EXPERIENCE-BOUNDARY','product','sku',100,1,100)", orderId, skuId);
      jdbc.update("INSERT INTO deliveries(order_id,status,shipped_at,delivered_at) VALUES (?,'DELIVERED',?,?)", orderId, local(), local());
      reviewId = persistence.insertReview(productId, otherId, 4, "earlier", Timestamp.from(NOW));
      questionId = service.createQuestion(productId, memberId, new QuestionCreateCommand("original")).questionId();
    });
  }

  @AfterEach
  void cleanUp() {
    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      jdbc.update("DELETE FROM admin_audit_logs WHERE admin_id=?", adminId);
      jdbc.update("DELETE FROM notifications WHERE member_id IN (?,?)", memberId, otherId);
      jdbc.update("DELETE FROM product_questions WHERE product_id=?", productId);
      jdbc.update("DELETE FROM reviews WHERE product_id=?", productId);
      jdbc.update("DELETE FROM deliveries WHERE order_id=?", orderId);
      jdbc.update("DELETE FROM order_items WHERE order_id=?", orderId);
      jdbc.update("DELETE FROM orders WHERE id=?", orderId);
      jdbc.update("DELETE FROM skus WHERE id=?", skuId);
      jdbc.update("DELETE FROM products WHERE id=?", productId);
      jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
      jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", adminId, memberId, otherId);
    });
  }

  @Test
  void completedMutationsAndAllFourPageTypesMapWithoutSessionSqlOrEntityLoads() throws Exception {
    List<Object> scalarViews = new ArrayList<>();
    var created = service.createReview(productId, memberId, new ReviewCreateCommand(5, "created"));
    scalarViews.add(created);
    scalarViews.add(service.updateReview(created.reviewId(), memberId, new ReviewPatchCommand(null, false, "updated", true)));
    scalarViews.add(service.myReview(productId, memberId));
    // A true tie must use the same timestamp binding path for both fixture rows.
    assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT created_at) FROM reviews WHERE product_id=?", Integer.class, productId)).isEqualTo(1);
    var ordered = service.reviews(productId, 0, 1);
    assertThat(ordered.items()).extracting(ReviewView::reviewId).containsExactly(created.reviewId());
    assertThat(ordered.totalElements()).isEqualTo(2);
    assertThat(ordered.totalPages()).isEqualTo(2);
    service.setReviewVisibility(reviewId, false, adminId);
    scalarViews.add(service.updateQuestion(questionId, memberId, new QuestionPatchCommand("updated question", true)));
    var answered = service.answerQuestion(questionId, "answer", adminId);
    scalarViews.add(answered);
    service.answerQuestion(questionId, "edited answer", adminId);
    verify(notifications, times(1)).create(memberId, "PRODUCT_QUESTION_ANSWERED", "PRODUCT_QUESTION", questionId);
    assertThatThrownBy(() -> service.updateQuestion(questionId, memberId, new QuestionPatchCommand("blocked", true)))
        .isInstanceOfSatisfying(ProductEngagementException.class, error -> assertThat(error.code()).isEqualTo("PRODUCT_QUESTION_LOCKED"));
    var reviews = service.reviews(productId, 0, 20);
    var adminReviews = service.adminReviews(productId, 0, 20);
    var questions = service.questions(productId, 0, 20);
    var adminQuestions = service.adminQuestions(productId, 0, 20);
    assertThat(reviews.totalElements()).isEqualTo(1);
    assertThat(adminReviews.items()).extracting(AdminReviewView::reviewId).containsExactly(created.reviewId(), reviewId);
    assertThat(adminReviews.items().getLast().visible()).isFalse();
    assertThat(adminReviews.items().getFirst().memberId()).isEqualTo(memberId);
    scalarViews.add(adminReviews.items().getFirst());
    scalarViews.add(questions.items().getFirst());
    assertThat(created.createdAt()).isEqualTo(NOW);
    assertThat(questions.items().getFirst().answer()).isEqualTo("edited answer");
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(TransactionSynchronizationManager.hasResource(emf)).isFalse();
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    for (Object view : scalarViews) {
      assertScalar(view);
      Object expected = EngagementResponseBoundaryTests.frozen(view);
      Object actual = expected.getClass().getMethod("from", view.getClass()).invoke(null, view);
      assertThat(actual).isEqualTo(expected);
      assertThat(json.writeValueAsString(actual)).isEqualTo(json.writeValueAsString(expected));
    }
    assertPage(reviews, ReviewListResponse.from(reviews));
    assertPage(adminReviews, AdminReviewListResponse.from(adminReviews));
    assertPage(questions, QuestionListResponse.from(questions));
    assertPage(adminQuestions, AdminQuestionListResponse.from(adminQuestions));
    assertThat(statistics.getPrepareStatementCount()).isZero();
    assertThat(statistics.getEntityLoadCount()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"notification", "answer-audit", "review-visibility-audit", "question-visibility-audit"})
  void failureAfterActualNotificationOrAuditWriteRollsBackAllEffects(String path) {
    var before = snapshot();
    if (path.equals("notification")) {
      doAnswer(call -> {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        call.callRealMethod();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE member_id=? AND reference_id=?", Integer.class, memberId, questionId)).isEqualTo(1);
        throw new IllegalStateException("notification rollback probe");
      }).when(notifications).create(anyLong(), anyString(), anyString(), anyLong());
    } else {
      doAnswer(call -> {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        call.callRealMethod();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=?", Integer.class, adminId)).isEqualTo(1);
        throw new IllegalStateException("audit flush rollback probe");
      }).when(audits).append(anyLong(), anyString(), anyString(), anyLong());
    }
    assertThatThrownBy(() -> {
      if (path.equals("review-visibility-audit")) service.setReviewVisibility(reviewId, false, adminId);
      else if (path.equals("question-visibility-audit")) service.setQuestionVisibility(questionId, false, adminId);
      else service.answerQuestion(questionId, "rollback answer", adminId);
    }).isInstanceOf(IllegalStateException.class).hasMessage(path.equals("notification") ? "notification rollback probe" : "audit flush rollback probe");
    assertThat(snapshot()).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE member_id=?", Integer.class, memberId)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=?", Integer.class, adminId)).isZero();
    if (path.equals("notification")) verify(audits, never()).append(anyLong(), anyString(), anyString(), anyLong());
  }

  private List<List<Map<String, Object>>> snapshot() {
    return List.of(jdbc.queryForList("SELECT * FROM reviews WHERE product_id=? ORDER BY id", productId),
        jdbc.queryForList("SELECT * FROM product_questions WHERE product_id=? ORDER BY id", productId));
  }

  private void assertPage(PageView<?> page, Object actual) throws Exception {
    List<Object> expectedItems = new ArrayList<>();
    for (Object item : page.items()) { assertScalar(item); expectedItems.add(EngagementResponseBoundaryTests.frozen(item)); }
    Object expected = actual.getClass().getConstructor(List.class, int.class, int.class, long.class, int.class)
        .newInstance(expectedItems, page.page(), page.size(), page.totalElements(), page.totalPages());
    assertThat(actual).isEqualTo(expected);
    assertThat(json.writeValueAsString(actual)).isEqualTo(json.writeValueAsString(expected));
  }

  private void assertScalar(Object view) throws Exception {
    for (RecordComponent field : view.getClass().getRecordComponents()) {
      Object value = field.getAccessor().invoke(view);
      assertThat(value == null || value instanceof Number || value instanceof String || value instanceof Boolean || value instanceof Instant).isTrue();
    }
  }

  private long member(String prefix, String role) {
    jdbc.update("INSERT INTO members(email,password_hash,role) VALUES (?,'fixture',?)", "experience-" + prefix + "@example.test", role);
    return lastId();
  }

  private long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
  private static LocalDateTime local() { return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC); }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock engagementBoundaryClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
  }
}
