package com.pawcycle.backend.commerce.payment.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentFailureConfirmationService;
import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentFailureConfirmationService.Outcome;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class ProcessingPaymentFailureConfirmationCommandTests {
  private static final String PREFIX = ProcessingPaymentFailureConfirmationCommand.PREFIX;
  private final ProcessingPaymentFailureConfirmationService confirmation =
      mock(ProcessingPaymentFailureConfirmationService.class);
  private final ApplicationContext context = mock(ApplicationContext.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  @BeforeEach
  void noBackgroundTriggers() {
    when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[0]);
  }

  @Test
  void defaultModeOnlyInspects() {
    when(confirmation.inspect(11L, 7L)).thenReturn(Outcome.INSPECTED_CONFIRMABLE);
    assertThat(command(valid()).execute()).isZero();
    assertThat(printed())
        .contains("RESULT=INSPECTED_CONFIRMABLE mode=inspect paymentId=11 operatorMemberId=7 reason=\"investigation note\"");
    verify(confirmation, never()).confirm(anyLong(), anyLong());
  }

  @Test
  void confirmModeWritesOnlyWithWorkerStopEvidence() {
    when(confirmation.confirm(11L, 7L)).thenReturn(Outcome.FAILURE_CONFIRMED);
    MockEnvironment environment = valid()
        .withProperty(PREFIX + "mode", "confirm")
        .withProperty(PREFIX + "workers-stopped-evidence", "change CHG-1: ingress closed, workers drained");
    assertThat(command(environment).execute()).isZero();
    assertThat(printed())
        .contains("RESULT=FAILURE_CONFIRMED mode=confirm paymentId=11")
        .contains("workersStoppedEvidence=\"change CHG-1: ingress closed, workers drained\"");
    verify(confirmation, never()).inspect(anyLong(), anyLong());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void confirmWithoutWorkerStopEvidenceIsRefusedBeforeAnyRead(String evidence) {
    MockEnvironment environment = valid().withProperty(PREFIX + "mode", "confirm");
    if (!evidence.isEmpty()) environment.setProperty(PREFIX + "workers-stopped-evidence", evidence);
    assertThat(command(environment).execute()).isEqualTo(2);
    assertThat(printed()).contains("RESULT=REFUSED_WORKERS_NOT_STOPPED");
    verifyNoInteractions(confirmation);
  }

  @ParameterizedTest
  @CsvSource({
    "FAILURE_CONFIRMED,0", "ALREADY_CONFIRMED,0", "CONFIRMED_AFTER_COMMIT_ERROR,0",
    "CHANGED_DURING_QUERY,3", "STOPPED_PROVIDER_DONE,3", "STOPPED_PROVIDER_UNKNOWN,3",
    "STOPPED_PROVIDER_OTHER,3", "REFUSED_RESERVATION_MISMATCH,3", "REFUSED_ORDER_HAS_SUCCEEDED_PAYMENT,3",
    "FAILED_ROLLED_BACK,4", "UNDETERMINED,4"
  })
  void confirmOutcomesMapToExitCodes(Outcome outcome, int exitCode) {
    when(confirmation.confirm(11L, 7L)).thenReturn(outcome);
    MockEnvironment environment = valid()
        .withProperty(PREFIX + "mode", "confirm")
        .withProperty(PREFIX + "workers-stopped-evidence", "ticket");
    assertThat(command(environment).execute()).isEqualTo(exitCode);
    assertThat(printed()).startsWith("RESULT=" + outcome.name() + " ");
  }

  @ParameterizedTest
  @EnumSource(Outcome.class)
  void everyOutcomeHasAnExitCodeOfZeroThreeOrFour(Outcome outcome) {
    when(confirmation.inspect(11L, 7L)).thenReturn(outcome);
    assertThat(command(valid()).execute()).isIn(0, 3, 4);
  }

  @ParameterizedTest
  @CsvSource({"payment-id,", "operator-member-id,", "reason,", "payment-id,0", "operator-member-id,-1", "mode,write"})
  void invalidInputIsRefused(String key, String value) {
    MockEnvironment environment = new MockEnvironment();
    for (String name : new String[] {"payment-id", "operator-member-id", "reason"})
      if (!name.equals(key)) environment.setProperty(PREFIX + name, valid().getProperty(PREFIX + name));
    if (value != null) environment.setProperty(PREFIX + key, value);
    assertThat(command(environment).execute()).isEqualTo(2);
    assertThat(printed()).contains("RESULT=REFUSED_INVALID_INPUT");
    verifyNoInteractions(confirmation);
  }

  @Test
  void unexpectedFailureStillPrintsResultAndUsesExitCodeFour() {
    when(confirmation.inspect(11L, 7L)).thenThrow(new IllegalStateException("connection lost"));
    assertThat(command(valid()).execute()).isEqualTo(4);
    assertThat(printed()).contains("RESULT=FAILED_EXCEPTION type=IllegalStateException mode=inspect");
  }

  @Test
  void activeBackgroundTriggerOrSuccessCommandInTheSameProcessIsRefused() {
    when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[] {"trigger"});
    assertThat(command(valid()).execute()).isEqualTo(2);
    assertThat(printed()).contains("RESULT=REFUSED_BACKGROUND_TRIGGER_ACTIVE");

    when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[0]);
    MockEnvironment both = valid()
        .withProperty(ProcessingPaymentRecoveryCommand.PREFIX + "enabled", "true");
    assertThat(command(both).execute()).isEqualTo(2);
    assertThat(printed()).contains("RESULT=REFUSED_INVALID_INPUT conflicting=");
    verifyNoInteractions(confirmation);
  }

  private MockEnvironment valid() {
    return new MockEnvironment()
        .withProperty(PREFIX + "payment-id", "11")
        .withProperty(PREFIX + "operator-member-id", "7")
        .withProperty(PREFIX + "reason", "investigation note");
  }

  private ProcessingPaymentFailureConfirmationCommand command(MockEnvironment environment) {
    output.reset();
    return new ProcessingPaymentFailureConfirmationCommand(
        confirmation, context, environment, new PrintStream(output, true, StandardCharsets.UTF_8), code -> {});
  }

  private String printed() {
    return output.toString(StandardCharsets.UTF_8);
  }
}
