package com.pawcycle.backend.catalog.maintenance;

import com.pawcycle.backend.catalog.application.CustomerCatalogImportService;
import com.pawcycle.backend.catalog.application.DemoCatalogManifestImportService.ImportResult;

public final class ProductionDemoCatalogImportResultHolder {

    private ImportResult result;
    private String summary;

    public void set(ImportResult result) {
        this.result = result;
        this.summary = result.summary();
    }

    public void set(CustomerCatalogImportService.ImportResult result) {
        this.result = null;
        this.summary = result.summary();
    }

    public ImportResult result() {
        return result;
    }

    public String summary() {
        return summary;
    }
}
