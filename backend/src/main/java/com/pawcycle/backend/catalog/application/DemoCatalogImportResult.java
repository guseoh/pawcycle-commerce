package com.pawcycle.backend.catalog.application;

public record DemoCatalogImportResult(
    DemoCatalogImportOperation operation,
    int categoriesCreated,
    int productsCreated,
    int skusCreated,
    int inventoriesCreated,
    int plansCreated,
    DemoCatalogImportPostflight postflight) {

  public String summary() {
    String postflightSummary =
        postflight == null ? "postflight=NOT_APPLIED" : postflight.summary();
    return "CATALOG_IMPORT_RESULT operation="
        + operation.name()
        + " status=PASS categories_created="
        + categoriesCreated
        + " products_created="
        + productsCreated
        + " skus_created="
        + skusCreated
        + " inventories_created="
        + inventoriesCreated
        + " plans_created="
        + plansCreated
        + " "
        + postflightSummary;
  }
}
