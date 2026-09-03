package com.pawcycle.backend.catalog.application;

public record CustomerCatalogSupplementImportResult(
    CustomerCatalogImportOperation operation,
    int expectedBrands,
    int expectedCategories,
    int expectedProducts,
    int expectedSkus,
    int brandsMissing,
    int categoriesMissing,
    int productsMissing,
    int skusMissing,
    int inventoriesMissing) {

  public String summary() {
    return "CUSTOMER_CATALOG_V3_IMPORT_RESULT operation="
        + operation.name()
        + " status=PASS expected="
        + expectedBrands
        + "/"
        + expectedCategories
        + "/"
        + expectedProducts
        + "/"
        + expectedSkus
        + " missing_or_created="
        + brandsMissing
        + "/"
        + categoriesMissing
        + "/"
        + productsMissing
        + "/"
        + skusMissing
        + " inventories_missing_or_created="
        + inventoriesMissing;
  }
}
