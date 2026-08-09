package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
	private static final Set<String> CUSTOM_METRIC_NAMES = Set.of(
			"pawcycle.subscription.reconciliation.executions",
			"pawcycle.subscription.reconciliation.processed",
			"pawcycle.subscription.reconciliation.failures",
			"pawcycle.subscription.reconciliation.duration",
			"pawcycle.subscription.idempotency.cleanup.executions",
			"pawcycle.subscription.idempotency.cleanup.duration",
			"pawcycle.subscription.idempotency.cleanup.rows",
			"pawcycle.subscription.idempotency.retained.rows",
			"pawcycle.subscription.idempotency.cleanup.candidates");

	@Autowired private WebApplicationContext applicationContext;
	@Autowired private MeterRegistry meterRegistry;
	@Autowired private Environment environment;
	@LocalServerPort private int port;
	private MockMvc mockMvc;

	@BeforeEach
	void configureMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
				.apply(springSecurity())
				.build();
	}

	@Test
	void onlyHealthAndPrometheusAreExposedAndPrometheusContainsApprovedMetrics() throws Exception {
		assertThat(environment.getProperty("management.endpoints.access.default")).isEqualTo("none");
		assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
				.isEqualTo("health,prometheus");
		assertThat(environment.getProperty("management.endpoint.health.access")).isEqualTo("read-only");
		assertThat(environment.getProperty("management.endpoint.prometheus.access")).isEqualTo("read-only");
		assertThat(environment.getProperty("management.endpoints.web.discovery.enabled")).isEqualTo("false");
		assertThat(environment.getProperty("management.endpoints.jmx.exposure.exclude")).isEqualTo("*");

		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		mockMvc.perform(get("/actuator"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator").with(user("observer")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/actuator/info"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/info").with(user("observer")))
				.andExpect(status().isForbidden());
		HttpResponse<Void> healthResponse = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
						.GET()
						.build(),
				HttpResponse.BodyHandlers.discarding());
		assertThat(healthResponse.statusCode()).isEqualTo(200);

		String prometheus = mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(prometheus).contains(
				"http_server_requests_seconds_count",
				"jvm_memory_used_bytes",
				"jvm_gc_memory_allocated_bytes_total",
				"jvm_threads_live_threads",
				"process_cpu_usage",
				"system_cpu_usage",
				"hikaricp_connections",
				"pawcycle_subscription_reconciliation_executions_total",
				"pawcycle_subscription_idempotency_cleanup_executions_total",
				"pawcycle_subscription_idempotency_retained_rows");
	}

	@Test
	void customMetricTagsStayWithinApprovedLowCardinalityValues() {
		Set<Meter> customMeters = meterRegistry.getMeters().stream()
				.filter(meter -> meter.getId().getName().startsWith("pawcycle.subscription."))
				.collect(Collectors.toSet());

		assertThat(customMeters.stream().map(meter -> meter.getId().getName()).collect(Collectors.toSet()))
				.containsExactlyInAnyOrderElementsOf(CUSTOM_METRIC_NAMES);
		assertThat(customMeters).allSatisfy(meter -> meter.getId().getTags().forEach(tag -> {
			assertThat(tag.getKey()).isIn("scope", "operation", "result");
			assertThat(tag.getValue()).isIn(
					"creation", "command", "repair", "delete", "success", "failure");
		}));
		assertThat(customMeters).allSatisfy(meter -> assertThat(meter.getId().getTags())
				.noneMatch(tag -> Set.of("memberId", "subscriptionId", "idempotencyKey")
						.contains(tag.getKey())));
	}
}
