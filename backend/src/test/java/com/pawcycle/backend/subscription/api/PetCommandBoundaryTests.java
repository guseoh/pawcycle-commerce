package com.pawcycle.backend.subscription.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.subscription.application.SubscriptionService;
import com.pawcycle.backend.subscription.model.*;
import java.math.BigDecimal;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

class PetCommandBoundaryTests {
  static IntStream patterns() { return IntStream.range(0, 27); }
  @ParameterizedTest @MethodSource("patterns")
  void jacksonTriStateIsCopiedWithoutNormalizationBeforeApplication(int pattern) throws Exception {
    int name = pattern % 3, breed = pattern / 3 % 3, weight = pattern / 9;
    var fields = new java.util.ArrayList<String>();
    if (name > 0) fields.add("\"name\":" + (name == 1 ? "null" : "\" pet \""));
    if (breed > 0) fields.add("\"breed\":" + (breed == 1 ? "null" : "\" breed \""));
    if (weight > 0) fields.add("\"weightKg\":" + (weight == 1 ? "null" : "12.3"));
    var request = new ObjectMapper().readValue("{" + String.join(",", fields) + "}", UpdatePetRequest.class);
    var command = new PetUpdateCommand(name == 2 ? " pet " : null, name > 0,
        breed == 2 ? " breed " : null, breed > 0, weight == 2 ? new BigDecimal("12.3") : null, weight > 0);
    var service = mock(SubscriptionService.class);
    when(service.updatePet(7, 9, command)).thenReturn(new SubscriptionReadModel.Pet(9, "pet", "DOG", null, null, false));
    var response = new SubscriptionController(service).updatePet(new AuthenticatedMemberPrincipal(7L), 9, request);
    assertThat(response.petId()).isEqualTo(9); verify(service).updatePet(7, 9, command);
    assertThat(command.hasChanges()).isEqualTo(pattern != 0);
  }
}
