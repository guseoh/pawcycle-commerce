package com.pawcycle.backend.catalog.application;

import com.pawcycle.backend.catalog.maintenance.persistence.CustomerCatalogImportPersistence;
import com.pawcycle.backend.catalog.maintenance.persistence.CustomerCatalogRealismCorrectionPersistence;
import com.pawcycle.backend.catalog.maintenance.persistence.DemoCatalogImportPersistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Treats the existing Data V1 baseline and Customer Catalog V3 supplement as one logical Customer
 * Catalog.
 */
@Service
public class CustomerCatalogImportService {

  private final DemoCatalogImportPersistence baseline;
  private final CustomerCatalogImportPersistence supplement;
  private final CustomerCatalogRealismCorrectionPersistence correction;

  public CustomerCatalogImportService(
      DemoCatalogImportPersistence baseline,
      CustomerCatalogImportPersistence supplement,
      CustomerCatalogRealismCorrectionPersistence correction) {
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
