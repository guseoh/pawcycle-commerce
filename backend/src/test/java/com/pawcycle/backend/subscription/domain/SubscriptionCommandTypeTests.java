package com.pawcycle.backend.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class SubscriptionCommandTypeTests {
  @Test void preservesExternalNormalizationAndUnknownCommandError() {
    assertThat(SubscriptionCommandType.fromExternalValue("change-plan")).isEqualTo(SubscriptionCommandType.CHANGE_PLAN);
    assertThat(SubscriptionCommandType.fromExternalValue("pause")).isEqualTo(SubscriptionCommandType.PAUSE);
    assertThatThrownBy(() -> SubscriptionCommandType.fromExternalValue("unknown"))
        .isInstanceOf(SubscriptionFailure.class).hasFieldOrPropertyWithValue("code", "SUBSCRIPTION_NOT_FOUND");
  }
}
