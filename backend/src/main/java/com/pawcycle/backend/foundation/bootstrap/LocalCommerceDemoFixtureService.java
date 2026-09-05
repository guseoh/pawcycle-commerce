package com.pawcycle.backend.foundation.bootstrap;

import com.pawcycle.backend.catalog.application.CatalogManifestImportException;
import com.pawcycle.backend.catalog.maintenance.persistence.DemoCatalogImportPersistence;
import com.pawcycle.backend.catalog.maintenance.persistence.ProductDetailSectionFixturePersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-integration & !test & !production & !prod")
public class LocalCommerceDemoFixtureService {

  static final int DEMO_PRODUCT_COUNT = 32;

  private final DemoCatalogImportPersistence importService;
  private final ProductDetailSectionFixturePersistence detailSectionFixtureService;

  @Value("${pawcycle.local-demo-catalog.manifest:classpath:catalog/demo-catalog.json}")
  private String manifestLocation;

  @Autowired
  public LocalCommerceDemoFixtureService(
      DemoCatalogImportPersistence importService,
      ProductDetailSectionFixturePersistence detailSectionFixtureService) {
    this.importService = importService;
    this.detailSectionFixtureService = detailSectionFixtureService;
  }

  public LocalCommerceDemoFixtureService(DemoCatalogImportPersistence importService) {
    this(importService, null);
  }

  @Transactional
  public void bootstrap() {
    try {
      importService.apply(manifestLocation);
      if (detailSectionFixtureService != null) {
        detailSectionFixtureService.bootstrap();
      }
    } catch (CatalogManifestImportException exception) {
      throw new LocalQaBootstrapException(
          "로컬 Commerce Demo fixture가 기존 데이터 또는 manifest와 충돌합니다: " + exception.getMessage(),
          exception);
    }
  }
}
