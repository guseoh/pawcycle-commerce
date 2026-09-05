package com.pawcycle.backend.foundation.bootstrap;

import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminPersistence;
import com.pawcycle.backend.catalog.application.CatalogManifestImportException;
import com.pawcycle.backend.catalog.maintenance.persistence.CustomerCatalogImportPersistence;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local compatibility wrapper around the shared Customer Catalog V3 importer. */
@Service
@Profile("local-integration & !test & !production & !prod")
public class LocalCustomerCatalogV3FixtureService {

  private final LocalCommerceDemoFixtureService baseline;
  private final CustomerCatalogImportPersistence supplement;

  public LocalCustomerCatalogV3FixtureService(
      JdbcTemplate jdbc,
      LocalCommerceDemoFixtureService baseline,
      CatalogAdminPersistence expansion,
      ProductListCacheInvalidator cache,
      Validator validator) {
    this.baseline = baseline;
    this.supplement = new CustomerCatalogImportPersistence(jdbc, expansion, cache, validator);
  }

  @Transactional
  public void bootstrap() {
    baseline.bootstrap();
    try {
      supplement.apply();
    } catch (CatalogManifestImportException exception) {
      throw new LocalQaBootstrapException(
          "Customer Catalog V3 적용이 기존 데이터 또는 manifest와 충돌합니다: " + exception.getMessage(),
          exception);
    }
  }
}
