package com.pawcycle.backend.subscription.v2;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class V2PetPlanApplicationServiceTests {
  @Test
  void nameOnlyPatchDoesNotRewriteOmittedFieldsFromStaleRead() {
    V2SubscriptionJdbcStore store = mock(V2SubscriptionJdbcStore.class);
    V2SubscriptionData.Pet current =
        new V2SubscriptionData.Pet(7L, "기존 이름", "DOG", "기존 품종", new BigDecimal("12.00"));
    when(store.findOwnedPet(10L, 7L)).thenReturn(current);
    V2PetPlanApplicationService service =
        new V2PetPlanApplicationService(
            store,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));

    service.updatePet(10L, 7L, Map.of("name", "새 이름"));

    verify(store).updatePet(10L, 7L, "새 이름", true, null, false, null, false);
  }
}
