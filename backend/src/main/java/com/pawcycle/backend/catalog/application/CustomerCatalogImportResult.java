package com.pawcycle.backend.catalog.application;

public record CustomerCatalogImportResult(
    DemoCatalogImportResult baseline,
    CustomerCatalogSupplementImportResult supplement,
    CustomerCatalogRealismImportResult correction) {

  public CustomerCatalogImportResult(
      DemoCatalogImportResult baseline, CustomerCatalogSupplementImportResult supplement) {
    this(
        baseline,
        supplement,
        new CustomerCatalogRealismImportResult(
            CustomerCatalogRealismOperation.VALIDATE, 0, 0, 0));
  }

  public String summary() {
    return "CUSTOMER_CATALOG_IMPORT_RESULT status=PASS baseline={"
        + baseline.summary()
        + "} supplement={"
        + supplement.summary()
        + "} correction={"
        + correction.summary()
        + "}";
  }
}
