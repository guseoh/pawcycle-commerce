package com.pawcycle.backend.interaction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InteractionServiceTests {
  private final InteractionEventPersistenceAdapter repository = mock(InteractionEventPersistenceAdapter.class);
  private final InteractionService service =
      new InteractionService(
          repository,
          new ObjectMapper(),
          Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void batchOverFiftyIsRejectedBeforePersistence() {
    List<InteractionEventRequest> events = new ArrayList<>();
    for (int index = 0; index < 51; index++) {
      events.add(
          new InteractionEventRequest(
              UUID.randomUUID().toString(), "PRODUCT_VIEW", null, null, null, null, null));
    }

    assertThatThrownBy(() -> service.record(10L, events))
        .isInstanceOf(InteractionException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    verifyNoInteractions(repository);
  }

  @Test
  void rawSearchTextIsRejected() {
    InteractionEventRequest event =
        new InteractionEventRequest(
            UUID.randomUUID().toString(),
            "SEARCH",
            null,
            null,
            null,
            null,
            Map.of("q", "private search text"));

    assertThatThrownBy(() -> service.record(10L, List.of(event)))
        .isInstanceOf(InteractionException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    verifyNoInteractions(repository);
  }

  @Test
  void petOwnershipIsValidatedBeforeEventInsert() {
    when(repository.petBelongsToMember(99L, 10L)).thenReturn(false);
    when(repository.productExists(1L)).thenReturn(true);
    InteractionEventRequest event =
        new InteractionEventRequest(
            UUID.randomUUID().toString(), "PRODUCT_VIEW", 1L, 99L, null, null, null);

    assertThatThrownBy(() -> service.record(10L, List.of(event)))
        .isInstanceOf(InteractionException.class)
        .hasFieldOrPropertyWithValue("code", "PET_NOT_FOUND");
    verify(repository, never()).insert(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void productViewRequiresProductId() {
    InteractionEventRequest event =
        new InteractionEventRequest(
            UUID.randomUUID().toString(), "PRODUCT_VIEW", null, null, null, null, null);

    assertThatThrownBy(() -> service.record(10L, List.of(event)))
        .isInstanceOf(InteractionException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    verifyNoInteractions(repository);
  }

  @Test
  void rawSearchTextInHasTextQueryIsRejected() {
    InteractionEventRequest event =
        new InteractionEventRequest(
            UUID.randomUUID().toString(),
            "FILTER",
            null,
            null,
            null,
            null,
            Map.of("hasTextQuery", "강아지 사료 서울 010-1234-5678"));

    assertThatThrownBy(() -> service.record(10L, List.of(event)))
        .isInstanceOf(InteractionException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    verifyNoInteractions(repository);
  }

  @Test
  void contextRejectsWrongScalarTypes() {
    InteractionEventRequest event =
        new InteractionEventRequest(
            UUID.randomUUID().toString(),
            "FILTER",
            null,
            null,
            null,
            null,
            Map.of("hasTextQuery", "true", "minPrice", "1000"));

    assertThatThrownBy(() -> service.record(10L, List.of(event)))
        .isInstanceOf(InteractionException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    verifyNoInteractions(repository);
  }

  @Test
  void recommendationEventsRequireProductAndRequestId() {
    InteractionEventRequest event =
        new InteractionEventRequest(
            UUID.randomUUID().toString(),
            "RECOMMENDATION_CLICK",
            1L,
            null,
            null,
            null,
            null);

    assertThatThrownBy(() -> service.record(10L, List.of(event)))
        .isInstanceOf(InteractionException.class)
        .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    verifyNoInteractions(repository);
  }
}
