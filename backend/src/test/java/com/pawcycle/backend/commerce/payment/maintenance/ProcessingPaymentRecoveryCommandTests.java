package com.pawcycle.backend.commerce.payment.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentRecoveryService;
import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentRecoveryService.Outcome;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class ProcessingPaymentRecoveryCommandTests {
  private final ProcessingPaymentRecoveryService recovery = mock(ProcessingPaymentRecoveryService.class);
  private final ApplicationContext context = mock(ApplicationContext.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  @Test
  void validInputRunsRecoveryAndMapsExitCodes() {
    when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[0]);
    when(recovery.recover(11L, 7L)).thenReturn(Outcome.RECOVERED);
    assertThat(command(valid()).execute()).isZero();
    assertThat(printed()).contains("RESULT=RECOVERED paymentId=11 operatorMemberId=7 reason=\"investigation note\"");

    when(recovery.recover(11L, 7L)).thenReturn(Outcome.STOPPED_PROVIDER_UNKNOWN);
    assertThat(command(valid()).execute()).isEqualTo(3);
    when(recovery.recover(11L, 7L)).thenReturn(Outcome.ALREADY_FINALIZED);
    assertThat(command(valid()).execute()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"payment-id", "operator-member-id", "reason"})
  void missingRequiredInputIsRefusedWithoutRecovery(String missing) {
    when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[0]);
    MockEnvironment environment = valid();
    environment.getPropertySources().get("mockProperties");
    MockEnvironment without = new MockEnvironment();
    for (String key : new String[] {"payment-id", "operator-member-id", "reason"})
      if (!key.equals(missing))
        without.setProperty(ProcessingPaymentRecoveryCommand.PREFIX + key, environment.getProperty(ProcessingPaymentRecoveryCommand.PREFIX + key));
    assertThat(command(without).execute()).isEqualTo(2);
    assertThat(printed()).contains("RESULT=REFUSED_INVALID_INPUT");
    verify(recovery, never()).recover(anyLong(), anyLong());
  }

  @Test
  void nonPositiveIdsAreRefused() {
    when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[0]);
    MockEnvironment environment = valid().withProperty(ProcessingPaymentRecoveryCommand.PREFIX + "payment-id", "0");
    assertThat(command(environment).execute()).isEqualTo(2);
    verify(recovery, never()).recover(anyLong(), anyLong());
  }

  @Test
  void unexpectedFailureStillPrintsResultAndUsesExitCodeFour() {
    when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[0]);
    when(recovery.recover(11L, 7L)).thenThrow(new IllegalStateException("lock wait timeout"));
    assertThat(command(valid()).execute()).isEqualTo(4);
    assertThat(printed())
        .contains("RESULT=FAILED_EXCEPTION type=IllegalStateException")
        .contains("reason=\"investigation note\"");
  }

  @Test
  void activeBackgroundTriggerInTheSameProcessIsRefused() {
    when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[] {"trigger"});
    assertThat(command(valid()).execute()).isEqualTo(2);
    assertThat(printed()).contains("RESULT=REFUSED_BACKGROUND_TRIGGER_ACTIVE");
    verify(recovery, never()).recover(anyLong(), anyLong());
  }

  private MockEnvironment valid() {
    return new MockEnvironment()
        .withProperty(ProcessingPaymentRecoveryCommand.PREFIX + "payment-id", "11")
        .withProperty(ProcessingPaymentRecoveryCommand.PREFIX + "operator-member-id", "7")
        .withProperty(ProcessingPaymentRecoveryCommand.PREFIX + "reason", "investigation note");
  }

  private ProcessingPaymentRecoveryCommand command(MockEnvironment environment) {
    output.reset();
    return new ProcessingPaymentRecoveryCommand(
        recovery, context, environment, new PrintStream(output, true, StandardCharsets.UTF_8), code -> {});
  }

  private String printed() {
    return output.toString(StandardCharsets.UTF_8);
  }
}
