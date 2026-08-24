package com.pawcycle.backend.subscription.v2.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubscriptionBurstMeasurementMarkerContractTests {

	private static final Instant STARTED_AT = Instant.parse("2026-08-24T00:00:00Z");
	private static final String IDENTITY = "phase10-subscription-burst-decision-10k-v1";
	private static final String SOURCE_SHA = "0123456789abcdef0123456789abcdef01234567";

	@TempDir Path tempDir;

	@Test
	void enrichedMarkerBindsDecisionIdentityAtCreateNewBoundary() throws Exception {
		Path marker = tempDir.resolve("decision-workload-started.json");
		SubscriptionBurstMeasurementService service = service(marker, IDENTITY, SOURCE_SHA, 10_000);

		service.assertWorkloadMarkerContract(10_000);
		service.writeWorkloadStartMarker();

		assertThat(Files.readString(marker)).isEqualTo(
				"{\"workloadIdentity\":\"phase10-subscription-burst-decision-10k-v1\","
						+ "\"sourceSha\":\"0123456789abcdef0123456789abcdef01234567\","
						+ "\"cohort\":10000,\"workloadInvocationStarted\":true,"
						+ "\"workloadStartedAtUtc\":\"2026-08-24T00:00:00Z\"}");
		assertThatThrownBy(service::writeWorkloadStartMarker)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("workload-start marker");
	}

	@Test
	void legacyMarkerPayloadRemainsUnchangedWhenIdentityContractIsAbsent() throws Exception {
		Path marker = tempDir.resolve("legacy-workload-started.json");
		SubscriptionBurstMeasurementService service = service(marker, "", "", 0);

		service.assertWorkloadMarkerContract(5_000);
		service.writeWorkloadStartMarker();

		assertThat(Files.readString(marker)).isEqualTo(
				"{\"workloadInvocationStarted\":true,\"workloadStartedAtUtc\":\"2026-08-24T00:00:00Z\"}");
	}

	@Test
	void mismatchedCohortRejectsIdentityContractBeforeMarkerCreation() {
		Path marker = tempDir.resolve("mismatched-workload-started.json");
		SubscriptionBurstMeasurementService service = service(marker, IDENTITY, SOURCE_SHA, 10_000);

		assertThatThrownBy(() -> service.assertWorkloadMarkerContract(9_999))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("identity contract");
		assertThat(marker).doesNotExist();
	}

	private SubscriptionBurstMeasurementService service(
			Path marker,
			String workloadIdentity,
			String sourceSha,
			int cohort) {
		return new SubscriptionBurstMeasurementService(
				null,
				null,
				Clock.fixed(STARTED_AT, ZoneOffset.UTC),
				marker.toString(),
				true,
				workloadIdentity,
				sourceSha,
				cohort);
	}
}
