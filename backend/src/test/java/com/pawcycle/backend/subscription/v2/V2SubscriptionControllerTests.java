package com.pawcycle.backend.subscription.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class V2SubscriptionControllerTests {

	private V2SubscriptionService service;
	private V2SubscriptionController controller;
	private AuthenticatedMemberPrincipal principal;

	@BeforeEach
	void setUp() {
		service = mock(V2SubscriptionService.class);
		controller = new V2SubscriptionController(service);
		principal = new AuthenticatedMemberPrincipal(7L);
	}

	@Test
	void commandRejectsMissingIfMatchBeforeReplayLookup() {
		assertThatThrownBy(() -> controller.command(principal, 11L, "pause", "replay-key", null, Map.of()))
				.isInstanceOf(V2ApiException.class)
				.hasFieldOrPropertyWithValue("status", 428)
				.hasFieldOrPropertyWithValue("code", "IF_MATCH_REQUIRED");

		verifyNoInteractions(service);
	}

	@Test
	void commandRejectsInvalidIfMatchBeforeReplayLookup() {
		assertThatThrownBy(() -> controller.command(principal, 11L, "pause", "replay-key", "invalid", Map.of()))
				.isInstanceOf(V2ApiException.class)
				.hasFieldOrPropertyWithValue("status", 400)
				.hasFieldOrPropertyWithValue("code", "IF_MATCH_INVALID");
		assertThatThrownBy(() -> controller.command(
				principal,
				11L,
				"pause",
				"replay-key",
				"\"999999999999999999999999\"",
				Map.of()))
				.isInstanceOf(V2ApiException.class)
				.hasFieldOrPropertyWithValue("status", 400)
				.hasFieldOrPropertyWithValue("code", "IF_MATCH_INVALID");

		verifyNoInteractions(service);
	}

	@Test
	void commandDelegatesAValidStaleIfMatchSoSuccessfulReplayCanIgnoreCurrentVersion() {
		V2SubscriptionService.V2Result replay = new V2SubscriptionService.V2Result(
				200,
				Map.of("subscriptionId", 11L, "version", 1L),
				null,
				"\"1\"",
				true);
		when(service.command(7L, 11L, "pause", "replay-key", "\"99\"", Map.of())).thenReturn(replay);

		ResponseEntity<Map<String, Object>> response = controller.command(
				principal,
				11L,
				"pause",
				"replay-key",
				"\"99\"",
				Map.of());

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getFirst("ETag")).isEqualTo("\"1\"");
		assertThat(response.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
		verify(service).command(7L, 11L, "pause", "replay-key", "\"99\"", Map.of());
	}
}
