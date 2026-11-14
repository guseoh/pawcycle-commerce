package com.pawcycle.backend.commerce.payment.maintenance;

import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentRecoveryService;
import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentRecoveryService.Outcome;
import java.io.PrintStream;
import java.util.List;
import java.util.function.IntConsumer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * One-shot maintenance command for #417 Phase 2a. It runs only in a non-web process with
 * {@code pawcycle.maintenance.recover-processing-payment.enabled=true} and refuses to start while any
 * background payment or subscription trigger is active in the same process.
 *
 * <p>Exit codes: 0 RECOVERED or ALREADY_FINALIZED, 3 stopped or refused without state change,
 * 2 invalid input or unsafe process.
 */
public final class ProcessingPaymentRecoveryCommand implements ApplicationRunner {
  static final String PREFIX = "pawcycle.maintenance.recover-processing-payment.";
  static final List<String> FORBIDDEN_TRIGGERS =
      List.of(
          "com.pawcycle.backend.commerce.checkout.application.CheckoutExpirationTrigger",
          "com.pawcycle.backend.commerce.billing.application.SubscriptionBillingTrigger",
          "com.pawcycle.backend.subscription.automation.SubscriptionOrderAutomationTrigger",
          "com.pawcycle.backend.subscription.automation.ScheduleReconciliationTrigger");

  private final ProcessingPaymentRecoveryService recovery;
  private final ApplicationContext context;
  private final Environment environment;
  private final PrintStream out;
  private final IntConsumer exit;

  ProcessingPaymentRecoveryCommand(
      ProcessingPaymentRecoveryService recovery,
      ApplicationContext context,
      Environment environment,
      PrintStream out,
      IntConsumer exit) {
    this.recovery = recovery;
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
    for (String trigger : FORBIDDEN_TRIGGERS) {
      if (activeBean(trigger)) {
        out.println("RESULT=REFUSED_BACKGROUND_TRIGGER_ACTIVE trigger=" + simpleName(trigger));
        return 2;
      }
    }
    Long paymentId = positiveLong("payment-id");
    Long operator = positiveLong("operator-member-id");
    String reason = environment.getProperty(PREFIX + "reason");
    if (paymentId == null || operator == null || reason == null || reason.isBlank()) {
      out.println("RESULT=REFUSED_INVALID_INPUT required=payment-id,operator-member-id,reason");
      return 2;
    }
    Outcome outcome = recovery.recover(paymentId, operator);
    out.println("RESULT=" + outcome.name() + " paymentId=" + paymentId + " operatorMemberId=" + operator);
    return outcome == Outcome.RECOVERED || outcome == Outcome.ALREADY_FINALIZED ? 0 : 3;
  }

  private boolean activeBean(String className) {
    try {
      return context.getBeanNamesForType(Class.forName(className)).length > 0;
    } catch (ClassNotFoundException missing) {
      return false;
    }
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
