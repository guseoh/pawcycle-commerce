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
  public CustomerCatalogImportResult validate() {
    return new CustomerCatalogImportResult(
        baseline.validate(), supplement.validate(), correction.validate());
  }

  @Transactional
  public CustomerCatalogImportResult apply() {
    return new CustomerCatalogImportResult(
        baseline.apply(), supplement.apply(), correction.apply());
  }

}
