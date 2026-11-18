package com.pawcycle.backend.commerce.payment.maintenance;

import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentFailureConfirmationService;
import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentFailureConfirmationService.Outcome;
import java.io.PrintStream;
import java.util.function.IntConsumer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * One-shot maintenance command for #417 Phase 2b (PWC-0134). It runs only in a non-web process with
 * {@code pawcycle.maintenance.confirm-processing-payment-failure.enabled=true} and refuses to start
 * while any background payment or subscription trigger is active in the same process.
 *
 * <p>{@code mode=inspect} (default) only reads. {@code mode=confirm} writes, and requires
 * {@code workers-stopped-evidence}: the operator's reference that new requests are blocked on every
 * instance that can process the payment and started work has ended. Without it the command refuses.
 *
 * <p>Exit codes: 0 FAILURE_CONFIRMED, ALREADY_CONFIRMED, CONFIRMED_AFTER_COMMIT_ERROR or
 * INSPECTED_CONFIRMABLE; 3 stopped or refused without state change; 2 invalid input or unsafe process;
 * 4 the write failed or its outcome is undetermined (re-read the state before anything else).
 */
public final class ProcessingPaymentFailureConfirmationCommand implements ApplicationRunner {
  static final String PREFIX = "pawcycle.maintenance.confirm-processing-payment-failure.";

  private final ProcessingPaymentFailureConfirmationService confirmation;
  private final ApplicationContext context;
  private final Environment environment;
  private final PrintStream out;
  private final IntConsumer exit;

  ProcessingPaymentFailureConfirmationCommand(
      ProcessingPaymentFailureConfirmationService confirmation,
      ApplicationContext context,
      Environment environment,
      PrintStream out,
      IntConsumer exit) {
    this.confirmation = confirmation;
    this.context = context;
    this.environment = environment;
    this.out = out;
    this.exit = exit;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    exit.accept(execute());
  }

  int execute() {
    for (String trigger : ProcessingPaymentRecoveryCommand.FORBIDDEN_TRIGGERS) {
      if (activeBean(trigger)) {
        out.println("RESULT=REFUSED_BACKGROUND_TRIGGER_ACTIVE trigger=" + simpleName(trigger));
        return 2;
      }
    }
    if (environment.getProperty(ProcessingPaymentRecoveryCommand.PREFIX + "enabled", Boolean.class, false)) {
      out.println("RESULT=REFUSED_INVALID_INPUT conflicting=recover-processing-payment.enabled");
      return 2;
    }
    Long paymentId = positiveLong("payment-id");
    Long operator = positiveLong("operator-member-id");
    String reason = text("reason");
    String mode = text("mode") == null ? "inspect" : text("mode");
    if (paymentId == null || operator == null || reason == null
        || !(mode.equals("inspect") || mode.equals("confirm"))) {
      out.println("RESULT=REFUSED_INVALID_INPUT required=payment-id,operator-member-id,reason mode=inspect|confirm");
      return 2;
    }
    String evidence = text("workers-stopped-evidence");
    if (mode.equals("confirm") && evidence == null) {
      out.println("RESULT=REFUSED_WORKERS_NOT_STOPPED required=workers-stopped-evidence mode=confirm");
      return 2;
    }
    String context = " mode=" + mode + " paymentId=" + paymentId + " operatorMemberId=" + operator
        + " reason=\"" + reason + "\""
        + (evidence == null ? "" : " workersStoppedEvidence=\"" + evidence + "\"");
    Outcome outcome;
    try {
      outcome = mode.equals("confirm")
          ? confirmation.confirm(paymentId, operator)
          : confirmation.inspect(paymentId, operator);
    } catch (RuntimeException failure) {
      out.println("RESULT=FAILED_EXCEPTION type=" + failure.getClass().getSimpleName() + context);
      return 4;
    }
    out.println("RESULT=" + outcome.name() + context);
    if (outcome.completed()) return 0;
    return outcome.failed() ? 4 : 3;
  }

  private boolean activeBean(String className) {
    try {
      return context.getBeanNamesForType(Class.forName(className)).length > 0;
    } catch (ClassNotFoundException missing) {
      return false;
    }
  }

  private String text(String key) {
    String value = environment.getProperty(PREFIX + key);
    return value == null || value.isBlank() ? null : value.strip();
  }

  private Long positiveLong(String key) {
    String value = environment.getProperty(PREFIX + key);
    if (value == null) return null;
    try {
      long parsed = Long.parseLong(value.trim());
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException invalid) {
      return null;
    }
  }

  private static String simpleName(String className) {
    return className.substring(className.lastIndexOf('.') + 1);
  }
}
