package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ObservabilityIntegrationTests {
  private static final Set<String> CUSTOM_METRIC_NAMES =
      Set.of(
          "pawcycle.subscription.automation.executions",
          "pawcycle.subscription.automation.processed.candidates",
          "pawcycle.subscription.automation.orders.created",
          "pawcycle.subscription.automation.failures",
          "pawcycle.subscription.automation.duplicate.noop",
          "pawcycle.subscription.automation.duration",
          "pawcycle.subscription.reconciliation.executions",
          "pawcycle.subscription.reconciliation.processed",
          "pawcycle.subscription.reconciliation.failures",
          "pawcycle.subscription.reconciliation.duration",
          "pawcycle.subscription.idempotency.cleanup.executions",
          "pawcycle.subscription.idempotency.cleanup.duration",
          "pawcycle.subscription.idempotency.cleanup.rows",
          "pawcycle.subscription.idempotency.retained.rows",
          "pawcycle.subscription.idempotency.cleanup.candidates",
          "pawcycle.subscription.idempotency.metrics.refresh.failures",
          "pawcycle.subscription.idempotency.metrics.refresh.last.success",
          "pawcycle.subscription.idempotency.metrics.refresh.age.seconds",
          "pawcycle.subscription.schedules.overdue",
          "pawcycle.subscription.schedules.held");

  @Autowired private WebApplicationContext applicationContext;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private Environment environment;
  @LocalServerPort private int port;
  private MockMvc mockMvc;

  @BeforeEach
  void configureMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
  }

  @Test
  void onlyHealthAndPrometheusAreExposedAndPrometheusContainsApprovedMetrics() throws Exception {
    assertThat(environment.getProperty("management.endpoints.access.default")).isEqualTo("none");
    assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health,prometheus");
    assertThat(environment.getProperty("management.endpoint.health.access")).isEqualTo("read-only");
    assertThat(environment.getProperty("management.endpoint.prometheus.access"))
        .isEqualTo("read-only");
    assertThat(environment.getProperty("management.endpoints.web.discovery.enabled"))
        .isEqualTo("false");
    assertThat(environment.getProperty("management.endpoints.jmx.exposure.exclude")).isEqualTo("*");

    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator").with(user("observer"))).andExpect(status().isForbidden());
    mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/info").with(user("observer"))).andExpect(status().isForbidden());
    HttpResponse<Void> healthResponse =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.discarding());
    assertThat(healthResponse.statusCode()).isEqualTo(200);

    String prometheus =
        mockMvc
            .perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(prometheus)
        .contains(
            "http_server_requests_seconds_count",
            "jvm_memory_used_bytes",
            "jvm_gc_memory_allocated_bytes_total",
            "jvm_threads_live_threads",
            "process_cpu_usage",
            "system_cpu_usage",
            "hikaricp_connections",
            // PawCycleDbConnectionAcquireFailure (local alert rule) reads this counter.
            "hikaricp_connections_timeout_total",
            "pawcycle_subscription_automation_executions_total",
            "pawcycle_subscription_automation_orders_total",
            "pawcycle_subscription_reconciliation_executions_total",
            "pawcycle_subscription_idempotency_cleanup_executions_total",
            "pawcycle_subscription_idempotency_retained_rows");
  }

  /**
   * Prometheus attaches job and instance to every scraped series and renames a clashing metric tag
   * to exported_job/exported_instance, so a PawCycle meter using them would never match its query.
   */
  @Test
  void pawcycleMetersDoNotUsePrometheusReservedLabels() {
    assertThat(meterRegistry.getMeters())
        .filteredOn(meter -> meter.getId().getName().startsWith("pawcycle."))
        .isNotEmpty()
        .allSatisfy(meter -> assertThat(meter.getId().getTags())
            .as("tags of %s", meter.getId().getName())
            .noneMatch(tag -> tag.getKey().equals("job") || tag.getKey().equals("instance")));
  }

  /**
   * Every PawCycle metric that a local alert rule or dashboard queries must exist in the scrape
   * output, so a renamed or removed meter cannot leave an empty panel or a silent alert (stage 2).
   */
  @Test
  void alertRulesAndDashboardsOnlyQueryExposedPawcycleMetrics() throws Exception {
    String prometheus =
        mockMvc
            .perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    java.util.Set<String> exposed = new java.util.TreeSet<>();
    for (String line : prometheus.split("\\R")) {
      if (line.isBlank() || line.startsWith("#")) continue;
      int end = line.indexOf('{');
      if (end < 0) end = line.indexOf(' ');
      exposed.add(line.substring(0, end));
    }

    java.nio.file.Path observability = java.nio.file.Path.of("../infra/local-integration/observability");
    java.util.Set<String> queried = new java.util.TreeSet<>();
    java.util.regex.Pattern name = java.util.regex.Pattern.compile("\\bpawcycle_[a-z0-9_]+");
    try (var files = java.nio.file.Files.walk(observability)) {
      for (java.nio.file.Path file : files
          .filter(path -> path.toString().endsWith(".json")
              || (path.toString().endsWith(".yml") && path.toString().contains("rules")))
          .toList()) {
        var matcher = name.matcher(java.nio.file.Files.readString(file));
        while (matcher.find()) queried.add(matcher.group());
      }
    }

    assertThat(queried).as("PawCycle metrics queried by rules and dashboards").isNotEmpty();
    assertThat(queried)
        .as("queried metrics missing from /actuator/prometheus")
        .allSatisfy(metric -> assertThat(exposed).contains(metric));
  }

  @Test
  void customMetricTagsStayWithinApprovedLowCardinalityValues() {
    Set<Meter> customMeters =
        meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().startsWith("pawcycle.subscription."))
            .collect(Collectors.toSet());

    assertThat(
            customMeters.stream().map(meter -> meter.getId().getName()).collect(Collectors.toSet()))
        .containsExactlyInAnyOrderElementsOf(CUSTOM_METRIC_NAMES);
    assertThat(customMeters)
        .allSatisfy(
            meter ->
                meter
                    .getId()
                    .getTags()
                    .forEach(
                        tag -> {
                          assertThat(tag.getKey()).isIn("scope", "operation", "result", "reason");
                          assertThat(tag.getValue())
                              .isIn(
                                  "creation", "command", "repair", "delete", "success", "failure",
                                  // schedules.held: the fixed hold reasons of subscription_schedules
                                  "missing_shipping_address", "missing_billing_method",
                                  "payment_retry_exhausted", "payment_retry_stock_unavailable",
                                  "order_stock_unavailable");
                        }));
    assertThat(customMeters)
        .allSatisfy(
            meter ->
                assertThat(meter.getId().getTags())
                    .noneMatch(
                        tag ->
                            Set.of(
                                    "memberId",
                                    "subscriptionId",
                                    "idempotencyKey",
                                    "orderId",
                                    "scheduleId")
                                .contains(tag.getKey())));
  }
}
