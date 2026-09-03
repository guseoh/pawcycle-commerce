package com.pawcycle.backend.subscription;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.pawcycle.backend.subscription.api.UpdatePetRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PetPlanApplicationServiceTests {
  @Test
  void nameOnlyPatchDoesNotRewriteOmittedFieldsFromStaleRead() {
    SubscriptionPersistenceAdapter store = mock(SubscriptionPersistenceAdapter.class);
    PetProjection current =
        new PetProjection(7L, "기존 이름", "DOG", "기존 품종", new BigDecimal("12.00"));
    when(store.findOwnedPet(10L, 7L)).thenReturn(current);
    PetPlanApplicationService service =
        new PetPlanApplicationService(
            store,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));

    UpdatePetRequest request = new UpdatePetRequest();
    request.readName("새 이름");
    service.updatePet(10L, 7L, request);

    verify(store).updatePet(10L, 7L, "새 이름", true, null, false, null, false);
  }
}
