package com.pawcycle.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pawcycle.backend.commerce.notification.application.NotificationService;
import com.pawcycle.backend.recommendation.application.RecommendationAiClient;
import com.pawcycle.backend.subscription.application.SubscriptionReconciliationApplicationService;
import com.pawcycle.backend.subscription.automation.ScheduleReconciliationTrigger;
import com.pawcycle.backend.subscription.automation.SubscriptionDeliveryReminderProcessor;
import com.pawcycle.backend.subscription.automation.SubscriptionOrderAutomationService;
import com.pawcycle.backend.subscription.automation.SubscriptionOrderAutomationTrigger;
import com.pawcycle.backend.subscription.persistence.SubscriptionDeliveryReminderPersistence;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@ActiveProfiles("test")
class RuntimeTopologyIntegrationTests {
  private static final String ROOT = "com.pawcycle.backend.";
  @Autowired private ApplicationContext context;

  @Test
  void discoversExistingRuntimeBeansAndExactlyOneDisabledAiClient() throws Exception {
    // Unconditional root components captured at main 5c077b8 before the move.
    Set<String> components = Set.of(
        "interaction.api.InteractionController", "interaction.api.InteractionExceptionHandler",
        "interaction.application.InteractionService", "interaction.persistence.InteractionEventPersistenceAdapter",
        "recommendation.api.ProductRecommendationController", "recommendation.api.RecommendationController",
        "recommendation.api.RecommendationExceptionHandler", "recommendation.application.ProductRecommendationService",
        "recommendation.application.RecommendationService", "recommendation.persistence.RecommendationQueryAdapter",
        "recommendation.infrastructure.ai.RecommendationAiConfiguration",
        "recommendation.infrastructure.metrics.RecommendationMetrics", "subscription.api.RepeatCommerceController",
        "subscription.api.SubscriptionController", "subscription.api.SubscriptionExceptionHandler",
        "subscription.application.PetPlanApplicationService", "subscription.application.SubscriptionService",
        "subscription.application.SubscriptionCommandApplicationService",
        "subscription.application.SubscriptionCreationApplicationService",
        "subscription.application.SubscriptionQueryApplicationService",
        "subscription.application.SubscriptionReconciliationApplicationService",
        "subscription.automation.SchedulingConfiguration", "subscription.automation.SubscriptionIdempotencyCleanupProcessor",
        "subscription.automation.SubscriptionIdempotencyCleanupService", "subscription.automation.SubscriptionMetrics",
        "subscription.automation.SubscriptionOrderAutomationMetrics", "subscription.automation.SubscriptionOrderAutomationService",
        "subscription.automation.SubscriptionOrderProcessor");
    for (String name : components) {
      assertThat(context.getBeansOfType(Class.forName(ROOT + name)))
          .as("Runtime discovery: %s", name).hasSize(1);
    }
    var clients = context.getBeansOfType(RecommendationAiClient.class);
    assertThat(clients).containsOnlyKeys("disabledRecommendationAiClient");
    assertThat(clients.get("disabledRecommendationAiClient").recommend(List.of(), List.of())).isEmpty();
  }

  @Test
  void preservesControllerRoutesAndAdviceTargets() throws Exception {
    Set<String> controllers = Set.of(
        ROOT + "subscription.api.SubscriptionController", ROOT + "subscription.api.RepeatCommerceController",
        ROOT + "recommendation.api.RecommendationController", ROOT + "recommendation.api.ProductRecommendationController",
        ROOT + "interaction.api.InteractionController");
    Set<String> actual = new TreeSet<>();
    context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class).getHandlerMethods().forEach((mapping, handler) -> {
      if (controllers.contains(handler.getBeanType().getName())) {
        for (var method : mapping.getMethodsCondition().getMethods()) {
          for (String path : mapping.getPatternValues()) actual.add(method.name() + " " + path);
        }
      }
    });
    assertThat(actual).containsExactlyInAnyOrderElementsOf(Set.of(
        "POST /api/interactions", "GET /api/recommendations/products", "GET /api/recommendations/popular",
        "GET /api/recommendations/trending", "GET /api/products/{productId}/related",
        "GET /api/products/{productId}/complementary", "GET /api/recommendations/reorder-timing",
        "GET /api/subscriptions/{subscriptionId}/cycle-suggestion", "GET /api/orders/{orderId}/subscription-options",
        "POST /api/pets", "GET /api/pets", "GET /api/pets/{petId}", "PATCH /api/pets/{petId}",
        "GET /api/subscription-plans", "GET /api/subscription-plan-versions/{planVersionId}",
        "POST /api/subscriptions", "GET /api/subscriptions", "GET /api/subscriptions/{subscriptionId}",
        "POST /api/subscriptions/{subscriptionId}/commands/{command}"));
    Map<String, Set<String>> adviceTargets = Map.of(
        "subscription.api.SubscriptionExceptionHandler", Set.of("SubscriptionController", "RepeatCommerceController"),
        "recommendation.api.RecommendationExceptionHandler", Set.of("RecommendationController", "ProductRecommendationController"),
        "interaction.api.InteractionExceptionHandler", Set.of("InteractionController"));
    for (var entry : adviceTargets.entrySet()) {
      var advice = Class.forName(ROOT + entry.getKey()).getAnnotation(RestControllerAdvice.class);
      assertThat(Arrays.stream(advice.assignableTypes()).map(Class::getSimpleName).toList())
          .containsExactlyInAnyOrderElementsOf(entry.getValue());
    }
  }

  @Test
  void preservesSchedulerAndMetricRegistration() throws Exception {
    Map<String, String> schedules = Map.of(
        "ScheduleReconciliationTrigger#reconcileActiveSubscriptions", "${pawcycle.subscription.reconciliation.fixed-delay-ms:60000}",
        "SubscriptionOrderAutomationTrigger#processDueSchedules", "${pawcycle.subscription.automation.fixed-delay-ms:60000}",
        "SubscriptionDeliveryReminderProcessor#process", "${pawcycle.subscription.delivery-reminder.fixed-delay-ms:60000}",
        "SubscriptionMetrics#refreshIdempotencyGauges", "${pawcycle.subscription.idempotency.metrics-refresh-ms:60000}");
    for (var entry : schedules.entrySet()) {
      String[] names = entry.getKey().split("#");
      var schedule = Class.forName(ROOT + "subscription.automation." + names[0])
          .getDeclaredMethod(names[1]).getAnnotation(Scheduled.class);
      assertThat(schedule.fixedDelayString()).isEqualTo(entry.getValue());
      assertThat(schedule.scheduler()).isEqualTo(names[0].equals("SubscriptionMetrics") ? "idempotencyMetricsTaskScheduler" : "");
    }
    var scheduler = context.getBean("idempotencyMetricsTaskScheduler", ThreadPoolTaskScheduler.class);
    assertThat(scheduler.getPoolSize()).isEqualTo(1);
    assertThat(scheduler.getThreadNamePrefix()).isEqualTo("idempotency-metrics-");
    var meters = context.getBean(MeterRegistry.class);
    assertThat(meters.find("pawcycle.recommendation.ai.outcomes").tag("result", "success").counter()).isNotNull();
    assertThat(meters.find("pawcycle.recommendation.ai.outcomes").tag("result", "fallback").counter()).isNotNull();
    assertThat(meters.find("pawcycle.recommendation.ai.call").timer()).isNotNull();
    assertThat(meters.find("pawcycle.subscription.reconciliation.executions").counter()).isNotNull();
    assertThat(meters.find("pawcycle.subscription.reconciliation.duration").timer()).isNotNull();
  }

  @Test
  void conditionalAutomationBeansRemainDisabledUnlessExplicitlyEnabled() {
    // Component-only context has no scheduling processor, so enabling discovery does not execute jobs.
    var runner = new ApplicationContextRunner()
        .withUserConfiguration(ScheduleReconciliationTrigger.class, SubscriptionOrderAutomationTrigger.class,
            SubscriptionDeliveryReminderProcessor.class)
        .withBean(SubscriptionReconciliationApplicationService.class, () -> mock(SubscriptionReconciliationApplicationService.class))
        .withBean(SubscriptionOrderAutomationService.class, () -> mock(SubscriptionOrderAutomationService.class))
        .withBean(SubscriptionDeliveryReminderPersistence.class, () -> mock(SubscriptionDeliveryReminderPersistence.class))
        .withBean(NotificationService.class, () -> mock(NotificationService.class))
        .withBean(Clock.class, Clock::systemUTC);
    runner.run(ctx -> {
      assertThat(ctx).doesNotHaveBean(ScheduleReconciliationTrigger.class)
          .doesNotHaveBean(SubscriptionOrderAutomationTrigger.class)
          .doesNotHaveBean(SubscriptionDeliveryReminderProcessor.class);
    });
    runner.withPropertyValues("pawcycle.subscription.reconciliation.enabled=true",
        "pawcycle.subscription.automation.enabled=true", "pawcycle.subscription.delivery-reminder.enabled=true")
        .run(ctx -> {
          assertThat(ctx).hasSingleBean(ScheduleReconciliationTrigger.class)
              .hasSingleBean(SubscriptionOrderAutomationTrigger.class)
              .hasSingleBean(SubscriptionDeliveryReminderProcessor.class);
          assertThat(ReflectionTestUtils.getField(ctx.getBean(SubscriptionOrderAutomationTrigger.class), "batchSize")).isEqualTo(100);
          assertThat(ReflectionTestUtils.getField(ctx.getBean(SubscriptionDeliveryReminderProcessor.class), "windowDays")).isEqualTo(3);
        });
  }
}
