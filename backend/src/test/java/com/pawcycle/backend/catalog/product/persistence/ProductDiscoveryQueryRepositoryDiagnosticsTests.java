package com.pawcycle.backend.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.product.application.ProductListView;
import com.pawcycle.backend.catalog.product.application.ProductSort;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ProductDiscoveryQueryRepositoryDiagnosticsTests {
  private static final String METRIC = "pawcycle.catalog.product.discovery.phase";
  private static final String DIAGNOSTICS_PROPERTY =
      "pawcycle.catalog.product.discovery.diagnostics.enabled";
  private static final Set<String> PHASES =
      Set.of("count-query", "list-query", "row-mapping", "repository-total");

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(RepositoryTestConfiguration.class);

  @Test
  void diagnosticsAreDisabledByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(SimpleMeterRegistry.class).getMeters()).isEmpty();
        });
  }

  @Test
  void disabledDiagnosticsPreserveDiscoveryResultWithoutRegisteringTimers() {
    contextRunner.run(
        context -> {
          EntityManager entityManager = context.getBean(EntityManager.class);
          stubSuccessfulQueries(entityManager, List.of(productRow()));

          ProductListView result = discover(context.getBean(ProductDiscoveryQueryRepository.class));

          assertThat(result.page()).isZero();
          assertThat(result.size()).isEqualTo(20);
          assertThat(result.totalElements()).isEqualTo(37);
          assertThat(result.items()).hasSize(1);
          assertThat(result.items().getFirst().productId()).isEqualTo(42L);
          assertThat(context.getBean(SimpleMeterRegistry.class).getMeters()).isEmpty();
        });
  }

  @Test
  void enabledDiagnosticsRecordOnlyTheFourFixedPhases() {
    contextRunner
        .withPropertyValues(DIAGNOSTICS_PROPERTY + "=true")
        .run(
            context -> {
              EntityManager entityManager = context.getBean(EntityManager.class);
              stubSuccessfulQueries(entityManager, List.of(productRow()));

              ProductListView result =
                  discover(context.getBean(ProductDiscoveryQueryRepository.class));

              assertThat(result.items()).hasSize(1);
              SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
              List<Meter> timers =
                  registry.getMeters().stream()
                      .filter(meter -> meter.getId().getName().equals(METRIC))
                      .toList();
              assertThat(registry.getMeters()).hasSize(4);
              assertThat(timers).hasSize(4);
              assertThat(timers)
                  .extracting(meter -> meter.getId().getTag("phase"))
                  .containsExactlyInAnyOrderElementsOf(PHASES);
              assertThat(timers)
                  .allSatisfy(
                      meter ->
                          assertThat(meter.getId().getTags())
                              .containsExactly(
                                  Tag.of(
                                      "phase", meter.getId().getTag("phase"))));

              for (String phase : PHASES) {
                Timer timer = registry.find(METRIC).tag("phase", phase).timer();
                assertThat(timer).isNotNull();
                assertThat(timer.count()).isEqualTo(1);
                assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
                assertThat(timer.max(TimeUnit.NANOSECONDS)).isPositive();
              }
            });
  }

  @Test
  void queryExceptionIsPropagatedAndTimersStopForCompletedPhases() {
    contextRunner
        .withPropertyValues(DIAGNOSTICS_PROPERTY + "=true")
        .run(
            context -> {
              EntityManager entityManager = context.getBean(EntityManager.class);
              Query countQuery = mock(Query.class);
              Query listQuery = mock(Query.class);
              RuntimeException expected = new IllegalStateException("database query failed");
              when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
              when(countQuery.getSingleResult()).thenReturn(37L);
              when(entityManager.createNativeQuery(anyString(), eq(Tuple.class)))
                  .thenReturn(listQuery);
              when(listQuery.setParameter(anyString(), any())).thenReturn(listQuery);
              when(listQuery.getResultList()).thenThrow(expected);

              assertThatThrownBy(
                      () -> discover(context.getBean(ProductDiscoveryQueryRepository.class)))
                  .isSameAs(expected);

              SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
              assertThat(timerCount(registry, "count-query")).isEqualTo(1);
              assertThat(timerCount(registry, "list-query")).isEqualTo(1);
              assertThat(timerCount(registry, "row-mapping")).isZero();
              assertThat(timerCount(registry, "repository-total")).isEqualTo(1);
            });
  }

  @Test
  void rowMappingExceptionIsPropagatedAndItsPhaseTimerIsRecorded() {
    contextRunner
        .withPropertyValues(DIAGNOSTICS_PROPERTY + "=true")
        .run(
            context -> {
              EntityManager entityManager = context.getBean(EntityManager.class);
              Tuple row = mock(Tuple.class);
              RuntimeException expected = new IllegalStateException("row mapping failed");
              when(row.get("product_id")).thenThrow(expected);
              stubSuccessfulQueries(entityManager, List.of(row));

              assertThatThrownBy(
                      () -> discover(context.getBean(ProductDiscoveryQueryRepository.class)))
                  .isSameAs(expected);

              SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
              assertThat(timerCount(registry, "count-query")).isEqualTo(1);
              assertThat(timerCount(registry, "list-query")).isEqualTo(1);
              assertThat(timerCount(registry, "row-mapping")).isEqualTo(1);
              assertThat(timerCount(registry, "repository-total")).isEqualTo(1);
            });
  }

  private static long timerCount(SimpleMeterRegistry registry, String phase) {
    return registry.find(METRIC).tag("phase", phase).timer().count();
  }

  private static ProductListView discover(ProductDiscoveryQueryRepository repository) {
    return repository.read(null, null, null, 0, 20, ProductSort.NEWEST);
  }

  private static void stubSuccessfulQueries(EntityManager entityManager, List<Tuple> rows) {
    Query countQuery = mock(Query.class);
    Query listQuery = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
    when(countQuery.getSingleResult()).thenReturn(37L);
    when(entityManager.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(listQuery);
    when(listQuery.setParameter(anyString(), any())).thenReturn(listQuery);
    when(listQuery.getResultList()).thenReturn(rows);
  }

  private static Tuple productRow() {
    Tuple row = mock(Tuple.class);
    Map<String, Object> values = new HashMap<>();
    values.put("product_id", 42L);
    values.put("name", "테스트 사료");
    values.put("pet_type", "DOG");
    values.put("short_description", "설명");
    values.put("thumbnail_url", "https://example.test/product.jpg");
    values.put("category_id", 7L);
    values.put("category_name", "사료");
    values.put("category_slug", "food");
    values.put("brand_id", 5L);
    values.put("brand_name", "브랜드");
    values.put("brand_slug", "brand");
    values.put("representative_price", new BigDecimal("10.50"));
    values.put("representative_sku_id", null);
    values.put("representative_sku_name", null);
    values.put("compare_at_price", null);
    values.put("average_rating", new BigDecimal("4.25"));
    values.put("review_count", 3L);
    values.put("has_subscribable", 1L);
    values.put("purchasable", 1L);
    when(row.get(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
    return row;
  }

  @Configuration(proxyBeanMethods = false)
  @Import(ProductDiscoveryQueryRepository.class)
  static class RepositoryTestConfiguration {
    @Bean
    EntityManager entityManager() {
      return mock(EntityManager.class);
    }

    @Bean
    SimpleMeterRegistry simpleMeterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
