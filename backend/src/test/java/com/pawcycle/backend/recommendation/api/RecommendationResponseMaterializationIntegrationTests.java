package com.pawcycle.backend.recommendation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.recommendation.application.ProductRecommendationService;
import com.pawcycle.backend.recommendation.application.RecommendationAiClient;
import com.pawcycle.backend.recommendation.application.RecommendationService;
import com.pawcycle.backend.recommendation.domain.RecommendationCategory;
import com.pawcycle.backend.recommendation.model.RecommendationView;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true", "pawcycle.recommendation.ai.enabled=false"})
@ActiveProfiles("test")
class RecommendationResponseMaterializationIntegrationTests {
  @Autowired private ProductRecommendationService products;
  @Autowired private RecommendationService personalized;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private ObjectMapper json;
  @Autowired private MeterRegistry meters;
  @MockitoBean private RecommendationAiClient ai;
  private long memberId, petId, categoryId, brandId;
  private long first, second;

  @BeforeEach
  void setUp() {
    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      jdbc.update("INSERT INTO members(email,password_hash,role) VALUES ('recommendation-boundary@example.test','fixture','USER')");
      memberId = lastId();
      jdbc.update("INSERT INTO pets(member_id,name,pet_type) VALUES (?,'boundary','DOG')", memberId);
      petId = lastId();
      jdbc.update("INSERT INTO categories(name,slug,display_order,active) VALUES ('boundary','recommendation-boundary',0,true)");
      categoryId = lastId();
      jdbc.update("INSERT INTO brands(name,slug,active,display_order) VALUES ('boundary','recommendation-boundary',true,0)");
      brandId = lastId();
      first = product("first");
      second = product("second");
      jdbc.update("INSERT INTO wishlist_items(member_id,product_id,created_at) VALUES (?,?,?)", memberId, first, LocalDateTime.of(2026, 10, 17, 15, 0));
    });
  }

  @AfterEach
  void cleanUp() {
    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      jdbc.update("DELETE FROM wishlist_items WHERE member_id=?", memberId);
      jdbc.update("DELETE FROM inventories WHERE sku_id IN (SELECT id FROM skus WHERE product_id IN (?,?))", first, second);
      jdbc.update("DELETE FROM skus WHERE product_id IN (?,?)", first, second);
      jdbc.update("DELETE FROM products WHERE id IN (?,?)", first, second);
      jdbc.update("DELETE FROM brands WHERE id=?", brandId);
      jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
      jdbc.update("DELETE FROM pets WHERE id=?", petId);
      jdbc.update("DELETE FROM members WHERE id=?", memberId);
    });
  }

  @Test
  void allFiveStrategiesMapAfterReadsAndProviderFailureWithoutSessionOrExtraSql() throws Exception {
    when(ai.recommend(anyList(), anyList())).thenThrow(new IllegalStateException("fake provider unavailable"));
    double fallbackBefore = meters.get("pawcycle.recommendation.ai.outcomes").tag("result", "fallback").counter().count();
    List<RecommendationView> views = List.of(products.popular("DOG", 10), products.trending("DOG", 10),
        products.related(first, 6), products.complementary(first, 6), personalized.recommend(memberId, petId));
    verify(ai, times(1)).recommend(anyList(), anyList());
    assertThat(meters.get("pawcycle.recommendation.ai.outcomes").tag("result", "fallback").counter().count()).isEqualTo(fallbackBefore + 1);
    assertThat(views.getLast().products().getFirst().productId()).isEqualTo(first);
    assertThat(views.stream().map(RecommendationView::requestId)).doesNotHaveDuplicates();
    for (int i = 0; i < 4; i++) {
      String strategy = List.of("POPULAR", "TRENDING", "RELATED", "COMPLEMENTARY").get(i);
      assertThat(views.get(i).products()).isNotEmpty().allSatisfy(item -> assertThat(item.strategy())
          .isEqualTo(strategy));
    }
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(TransactionSynchronizationManager.hasResource(emf)).isFalse();
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    for (RecommendationView view : views) {
      assertThat(UUID.fromString(view.requestId())).isNotNull();
      for (var item : view.products()) {
        assertThat(item.getClass().isRecord()).isTrue();
        if (item.category() != null) assertThat(item.category()).isExactlyInstanceOf(RecommendationCategory.class);
      }
      var expected = RecommendationResponseBoundaryTests.frozen(view);
      var actual = RecommendationResponse.from(view);
      assertThat(actual).isEqualTo(expected);
      assertThat(actual.requestId()).isEqualTo(view.requestId());
      assertThat(json.writeValueAsString(actual)).isEqualTo(json.writeValueAsString(expected));
    }
    assertThat(statistics.getPrepareStatementCount()).isZero();
    assertThat(statistics.getEntityLoadCount()).isZero();
    verify(ai, times(1)).recommend(anyList(), anyList());
  }

  private long product(String suffix) {
    jdbc.update("INSERT INTO products(brand_id,category_id,name,short_description,pet_type,display_status) VALUES (?, ?, ?, 'fixture', 'DOG','PUBLIC')", brandId, categoryId, "recommendation-boundary-" + suffix);
    long id = lastId();
    jdbc.update("INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES (?,?,'fixture',100,true,0,'ACTIVE')", id, "REC-BOUNDARY-" + suffix);
    long sku = lastId();
    jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,10,0,0)", sku);
    return id;
  }

  private long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock recommendationBoundaryClock() { return Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneOffset.UTC); }
  }
}
