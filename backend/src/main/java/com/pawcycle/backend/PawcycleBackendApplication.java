package com.pawcycle.backend;

import com.pawcycle.backend.catalog.maintenance.ProductionDemoCatalogImportCommand;
import com.pawcycle.backend.member.maintenance.ProductionAuthSmokeMemberBootstrap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration;

// Sessions use a dedicated Redis only when pawcycle.session.store=redis (SessionStoreConfiguration).
@SpringBootApplication(exclude = SessionDataRedisAutoConfiguration.class)
public class PawcycleBackendApplication {
  static final String RECOVERY_PROPERTY = "pawcycle.maintenance.recover-processing-payment.enabled";
  static final String RECOVERY_ENVIRONMENT = "PAWCYCLE_MAINTENANCE_RECOVER_PROCESSING_PAYMENT_ENABLED";
  static final String FAILURE_CONFIRMATION_PROPERTY =
      "pawcycle.maintenance.confirm-processing-payment-failure.enabled";
  static final String FAILURE_CONFIRMATION_ENVIRONMENT =
      "PAWCYCLE_MAINTENANCE_CONFIRM_PROCESSING_PAYMENT_FAILURE_ENABLED";

  public static void main(String[] args) {
    int catalogImportExitCode = ProductionDemoCatalogImportCommand.runIfRequested(args);
    if (catalogImportExitCode != ProductionDemoCatalogImportCommand.NOT_REQUESTED) {
      if (catalogImportExitCode != 0) {
        System.exit(catalogImportExitCode);
      }
      return;
    }
    if (isProcessingPaymentRecovery(args, System.getProperty(RECOVERY_PROPERTY), System.getenv())
        || isProcessingPaymentFailureConfirmation(
            args, System.getProperty(FAILURE_CONFIRMATION_PROPERTY), System.getenv())) {
      SpringApplication.run(PawcycleBackendApplication.class, args);
      return;
    }
    int maintenanceExitCode = ProductionAuthSmokeMemberBootstrap.runIfRequested(args);
    if (maintenanceExitCode != ProductionAuthSmokeMemberBootstrap.NOT_REQUESTED) {
      if (maintenanceExitCode != 0) {
        System.exit(maintenanceExitCode);
      }
      return;
    }
    SpringApplication.run(PawcycleBackendApplication.class, args);
  }

  /**
   * The #417 recovery command is a non-web process too. Route it before the auth-smoke bootstrap,
   * which otherwise treats every non-web invocation as its own request and exits.
   */
  static boolean isProcessingPaymentRecovery(
      String[] args, String systemProperty, Map<String, String> environment) {
    return requested(args, RECOVERY_PROPERTY, systemProperty, environment.get(RECOVERY_ENVIRONMENT));
  }

  /** The #417 Phase 2b failure confirmation command is routed the same way. */
  static boolean isProcessingPaymentFailureConfirmation(
      String[] args, String systemProperty, Map<String, String> environment) {
    return requested(
        args, FAILURE_CONFIRMATION_PROPERTY, systemProperty, environment.get(FAILURE_CONFIRMATION_ENVIRONMENT));
  }

  private static boolean requested(String[] args, String property, String systemProperty, String environment) {
    for (String arg : args) {
      if (arg.equals("--" + property) || arg.equalsIgnoreCase("--" + property + "=true")) return true;
    }
    return "true".equalsIgnoreCase(systemProperty) || "true".equalsIgnoreCase(environment);
  }
}
