package com.pawcycle.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.subscription.api.CreateSubscriptionRequest;
import com.pawcycle.backend.subscription.api.PageResponse;
import com.pawcycle.backend.subscription.api.SubscriptionCommandRequest;
import com.pawcycle.backend.subscription.api.SubscriptionDetailResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class SubscriptionControllerTests {

  private SubscriptionService service;
  private SubscriptionController controller;
  private AuthenticatedMemberPrincipal principal;

  @BeforeEach
  void setUp() {
    service = mock(SubscriptionService.class);
    controller = new SubscriptionController(service);
    principal = new AuthenticatedMemberPrincipal(7L);
  }

  @Test
  void commandRejectsMissingIfMatchBeforeReplayLookup() {
    assertThatThrownBy(
            () -> controller.command(principal, 11L, "pause", "replay-key", null, SubscriptionCommandRequest.empty()))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("status", 428)
        .hasFieldOrPropertyWithValue("code", "IF_MATCH_REQUIRED");

    verifyNoInteractions(service);
  }

  @Test
  void commandRejectsInvalidIfMatchBeforeReplayLookup() {
    assertThatThrownBy(
            () -> controller.command(principal, 11L, "pause", "replay-key", "invalid", SubscriptionCommandRequest.empty()))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("status", 400)
        .hasFieldOrPropertyWithValue("code", "IF_MATCH_INVALID");
    assertThatThrownBy(
            () ->
                controller.command(
                    principal,
                    11L,
                    "pause",
                    "replay-key",
                    "\"999999999999999999999999\"",
                    SubscriptionCommandRequest.empty()))
        .isInstanceOf(SubscriptionApiException.class)
        .hasFieldOrPropertyWithValue("status", 400)
        .hasFieldOrPropertyWithValue("code", "IF_MATCH_INVALID");

    verifyNoInteractions(service);
  }

  @Test
  void commandDelegatesAValidStaleIfMatchSoSuccessfulReplayCanIgnoreCurrentVersion() {
    SubscriptionResult replay =
        new SubscriptionResult(
            200, detail(11L, "PAUSED", 1L), null, "\"1\"", true);
    when(service.command(
            7L,
            11L,
            "pause",
            "replay-key",
            "\"99\"",
            SubscriptionCommandRequest.empty()))
        .thenReturn(replay);

    ResponseEntity<SubscriptionDetailResponse> response =
        controller.command(
            principal,
            11L,
            "pause",
            "replay-key",
            "\"99\"",
            SubscriptionCommandRequest.empty());

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getFirst("ETag")).isEqualTo("\"1\"");
    assertThat(response.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
    verify(service)
        .command(
            7L,
            11L,
            "pause",
            "replay-key",
            "\"99\"",
            SubscriptionCommandRequest.empty());
  }

  @Test
  void createSubscriptionBuildsLocationAndEtagAtTheHttpBoundary() {
    SubscriptionResult created =
        new SubscriptionResult(
            201,
            detail(11L, "ACTIVE", 0L),
            "/api/subscriptions/11",
            "\"0\"",
            false);
    CreateSubscriptionRequest request = new CreateSubscriptionRequest(3L, 4L, 4);
    when(service.createSubscription(7L, "create-key", request)).thenReturn(created);

    ResponseEntity<SubscriptionDetailResponse> response =
        controller.createSubscription(principal, "create-key", request);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getHeaders().getFirst("Location")).isEqualTo("/api/subscriptions/11");
    assertThat(response.getHeaders().getFirst("ETag")).isEqualTo("\"0\"");
    assertThat(response.getHeaders().getFirst("Idempotency-Replayed")).isNull();
  }

  private static SubscriptionDetailResponse detail(long id, String status, long version) {
    return new SubscriptionDetailResponse(
        id,
        status,
        version,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        new PageResponse<>(0, 20, 0, List.of()),
        new PageResponse<>(0, 20, 0, List.of()));
  }
}
