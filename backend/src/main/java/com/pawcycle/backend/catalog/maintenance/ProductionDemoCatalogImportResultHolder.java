package com.pawcycle.backend.catalog.maintenance;

import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService.ImportResult;

public final class ProductionDemoCatalogImportResultHolder {

	private ImportResult result;

	public void set(ImportResult result) {
		this.result = result;
	}

	public ImportResult result() {
		return result;
	}
}
