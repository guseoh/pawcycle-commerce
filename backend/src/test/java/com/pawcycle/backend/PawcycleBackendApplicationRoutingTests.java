package com.pawcycle.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** The #417 recovery command must reach SpringApplication.run instead of the auth-smoke bootstrap. */
class PawcycleBackendApplicationRoutingTests {
  private static final String ENABLED =
      "--" + PawcycleBackendApplication.RECOVERY_PROPERTY + "=true";

  @Test
  void onlyExplicitRecoveryRequestsAreRouted() {
    assertThat(PawcycleBackendApplication.isProcessingPaymentRecovery(
        new String[] {"--spring.main.web-application-type=none", ENABLED}, null, Map.of())).isTrue();
    assertThat(PawcycleBackendApplication.isProcessingPaymentRecovery(new String[0], "true", Map.of()))
        .isTrue();
    assertThat(PawcycleBackendApplication.isProcessingPaymentRecovery(
        new String[0], null, Map.of(PawcycleBackendApplication.RECOVERY_ENVIRONMENT, "true"))).isTrue();
    assertThat(PawcycleBackendApplication.isProcessingPaymentRecovery(
        new String[] {"--spring.main.web-application-type=none"}, null, Map.of())).isFalse();
    assertThat(PawcycleBackendApplication.isProcessingPaymentRecovery(
        new String[] {"--" + PawcycleBackendApplication.RECOVERY_PROPERTY + "=false"}, "false", Map.of()))
        .isFalse();
  }
}
