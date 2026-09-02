package com.pawcycle.backend.catalog.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Treats the existing Data V1 baseline and Customer Catalog V3 supplement as one logical Customer
 * Catalog.
 */
@Service
public class CustomerCatalogImportService {

  private final DemoCatalogManifestImportService baseline;
  private final CustomerCatalogV3ImportService supplement;
  private final CustomerCatalogRealismCorrectionService correction;

  public CustomerCatalogImportService(
      DemoCatalogManifestImportService baseline,
      CustomerCatalogV3ImportService supplement,
      CustomerCatalogRealismCorrectionService correction) {
    this.baseline = baseline;
    this.supplement = supplement;
    this.correction = correction;
  }

  @Transactional
  public ImportResult validate() {
    return new ImportResult(baseline.validate(), supplement.validate(), correction.validate());
  }

  @Transactional
  public ImportResult apply() {
    return new ImportResult(baseline.apply(), supplement.apply(), correction.apply());
  }

  public record ImportResult(
      DemoCatalogManifestImportService.ImportResult baseline,
      CustomerCatalogV3ImportService.ImportResult supplement,
      CustomerCatalogRealismCorrectionService.ImportResult correction) {

    public ImportResult(
        DemoCatalogManifestImportService.ImportResult baseline,
        CustomerCatalogV3ImportService.ImportResult supplement) {
      this(
          baseline,
          supplement,
          new CustomerCatalogRealismCorrectionService.ImportResult(
              CustomerCatalogRealismCorrectionService.Operation.VALIDATE, 0, 0, 0));
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
}
