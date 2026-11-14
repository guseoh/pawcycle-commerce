package com.pawcycle.backend;

import com.pawcycle.backend.catalog.maintenance.ProductionDemoCatalogImportCommand;
import com.pawcycle.backend.member.maintenance.ProductionAuthSmokeMemberBootstrap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PawcycleBackendApplication {
  static final String RECOVERY_PROPERTY = "pawcycle.maintenance.recover-processing-payment.enabled";
  static final String RECOVERY_ENVIRONMENT = "PAWCYCLE_MAINTENANCE_RECOVER_PROCESSING_PAYMENT_ENABLED";

  public static void main(String[] args) {
    int catalogImportExitCode = ProductionDemoCatalogImportCommand.runIfRequested(args);
    if (catalogImportExitCode != ProductionDemoCatalogImportCommand.NOT_REQUESTED) {
      if (catalogImportExitCode != 0) {
        System.exit(catalogImportExitCode);
      }
      return;
    }
    if (isProcessingPaymentRecovery(args, System.getProperty(RECOVERY_PROPERTY), System.getenv())) {
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
    for (String arg : args) {
      if (arg.equals("--" + RECOVERY_PROPERTY) || arg.equalsIgnoreCase("--" + RECOVERY_PROPERTY + "=true"))
        return true;
    }
    return "true".equalsIgnoreCase(systemProperty)
        || "true".equalsIgnoreCase(environment.get(RECOVERY_ENVIRONMENT));
  }
}
