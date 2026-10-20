package com.pawcycle.backend.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.subscription.api.CreateSubscriptionRequest;
import com.pawcycle.backend.subscription.api.SubscriptionCommandRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SubscriptionRequestBoundaryTests {
  private final ObjectMapper json = new ObjectMapper();
  private final SubscriptionApplicationSupport support = new SubscriptionApplicationSupport(json, Clock.systemUTC());

  static IntStream masks() { return IntStream.range(0, 64); }

  @ParameterizedTest @MethodSource("masks")
  void everyNullableCommandCombinationKeepsLegacyBytesAndSha256(int mask) {
    var request = new SubscriptionCommandRequest(
        (mask & 1) == 0 ? null : Long.MAX_VALUE, (mask & 2) == 0 ? null : 4L,
        (mask & 4) == 0 ? null : 8, (mask & 8) == 0 ? null : LocalDate.of(2026, 12, 31),
        (mask & 16) == 0 ? null : 9L, (mask & 32) == 0 ? null : Integer.MAX_VALUE);
    assertThat(json.writeValueAsBytes(request.toCommand())).containsExactly(json.writeValueAsBytes(request));
    assertThat(support.fingerprint(request.toCommand())).isEqualTo(support.fingerprint(request));
  }

  @Test
  void creationAndEmptyCommandsKeepFieldOrderNullsAndValues() {
    for (var request : java.util.List.of(new CreateSubscriptionRequest(3L, 4L, 4),
        new CreateSubscriptionRequest(null, null, null), new CreateSubscriptionRequest(Long.MAX_VALUE, 0L, -1))) {
      assertThat(json.writeValueAsBytes(request.toCommand())).containsExactly(json.writeValueAsBytes(request));
      assertThat(support.fingerprint(request.toCommand())).isEqualTo(support.fingerprint(request));
    }
    assertThat(json.writeValueAsString(new CreateSubscriptionRequest(3L, 4L, 4).toCommand()))
        .isEqualTo("{\"petId\":3,\"planVersionId\":4,\"deliveryCycleWeeks\":4}");
    assertThat(json.writeValueAsString(SubscriptionCommandRequest.empty().toCommand()))
        .isEqualTo("{\"petId\":null,\"planVersionId\":null,\"deliveryCycleWeeks\":null,\"scheduledDate\":null,\"skuId\":null,\"quantity\":null}");
    assertThat(SubscriptionCommandRequest.empty().toCommand())
        .isEqualTo(com.pawcycle.backend.subscription.model.SubscriptionCommand.empty());
    assertThat(support.fingerprint(new CreateSubscriptionRequest(3L, 4L, 4).toCommand()))
        .isEqualTo("ea505cf9c6f029f53bb72da7c39111e2540ec9be23507a0db57dc11ac5a0f78b");
    assertThat(support.fingerprint(SubscriptionCommandRequest.empty().toCommand()))
        .isEqualTo("515e75a017fc6fac677a482a77adabbb7195d078a596d1909df65c4f1111e594");
  }
}
