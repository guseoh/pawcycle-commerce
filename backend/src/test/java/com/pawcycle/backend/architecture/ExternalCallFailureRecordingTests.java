package com.pawcycle.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Every class that calls an external provider port must also use {@code ExternalCallFailures}, so a
 * provider failure that is turned into a fallback (UNKNOWN, NO_RESPONSE, empty AI result) is never
 * swallowed without a log line and a metric again (stage 2, O2/O3).
 */
class ExternalCallFailureRecordingTests {
  private static final String ROOT = "com.pawcycle.backend.";
  private static final String RECORDER = ROOT + "common.external.ExternalCallFailures";
  private static final Set<String> PROVIDER_PORTS = Set.of(
      ROOT + "commerce.payment.application.TossPaymentAdapter",
      ROOT + "commerce.refund.infrastructure.toss.TossRefundAdapter",
      ROOT + "commerce.billing.infrastructure.toss.TossBillingAdapter",
      ROOT + "recommendation.application.RecommendationAiClient",
      ROOT + "catalog.engagement.application.ReviewSummaryAiClient",
      ROOT + "catalog.product.application.ProductComparisonAiClient");
  /** Capability checks only; they never reach the provider. */
  private static final Set<String> NON_CALLS = Set.of("isConfigured", "browserTestEnabled");
  /**
   * Callers that let the provider failure propagate to the client instead of converting it, so
   * there is nothing swallowed to record. Adding an entry needs that reason in review.
   */
  private static final Map<String, String> PROPAGATING_CALLERS = Map.of(
      ROOT + "commerce.billing.application.BillingApplicationService",
      "issueBillingKey failure is returned to the client as an error; no fallback state");

  @Test
  void providerCallersRecordFailures() {
    JavaClasses classes = new ClassFileImporter().importPath(Path.of("build/classes/java/main"));
    Set<String> callers = new TreeSet<>();
    Set<String> missing = new TreeSet<>();
    for (JavaClass type : classes) {
      if (PROVIDER_PORTS.contains(type.getName()) || implementsPort(type)) continue;
      boolean callsProvider = type.getMethodCallsFromSelf().stream().anyMatch(call ->
          PROVIDER_PORTS.contains(call.getTargetOwner().getName())
              && !NON_CALLS.contains(call.getName()));
      if (!callsProvider) continue;
      String outer = outerName(type);
      callers.add(outer);
      if (PROPAGATING_CALLERS.containsKey(outer)) continue;
      if (!dependsOnRecorder(classes, outer)) missing.add(outer);
    }
    assertThat(callers).as("provider callers were found").isNotEmpty();
    assertThat(missing)
        .as("Provider callers must record failures with ExternalCallFailures")
        .isEmpty();
    assertThat(callers).containsAll(PROPAGATING_CALLERS.keySet());
  }

  private static boolean implementsPort(JavaClass type) {
    return type.getAllRawInterfaces().stream().anyMatch(port -> PROVIDER_PORTS.contains(port.getName()));
  }

  private static String outerName(JavaClass type) {
    String name = type.getName();
    int nested = name.indexOf('$');
    return nested < 0 ? name : name.substring(0, nested);
  }

  /** The outer class or any of its nested/lambda classes depends on the recorder. */
  private static boolean dependsOnRecorder(JavaClasses classes, String outer) {
    for (JavaClass type : classes) {
      if (!outerName(type).equals(outer)) continue;
      if (type.getDirectDependenciesFromSelf().stream()
          .anyMatch(dependency -> dependency.getTargetClass().getName().equals(RECORDER))) {
        return true;
      }
    }
    return false;
  }
}
