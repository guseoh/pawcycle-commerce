package com.pawcycle.backend;

import com.pawcycle.backend.catalog.maintenance.ProductionDemoCatalogImportCommand;
import com.pawcycle.backend.member.maintenance.ProductionAuthSmokeMemberBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PawcycleBackendApplication {

	public static void main(String[] args) {
		int catalogImportExitCode = ProductionDemoCatalogImportCommand.runIfRequested(args);
		if (catalogImportExitCode != ProductionDemoCatalogImportCommand.NOT_REQUESTED) {
			if (catalogImportExitCode != 0) {
				System.exit(catalogImportExitCode);
			}
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

}
