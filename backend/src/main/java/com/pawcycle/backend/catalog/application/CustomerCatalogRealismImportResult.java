package com.pawcycle.backend.catalog.application;

public record CustomerCatalogRealismImportResult(
    CustomerCatalogRealismOperation operation,
    int brandsUpdated,
    int productsUpdated,
    int imagesUpdated) {

  public String summary() {
    return "operation="
        + operation
        + ",brandsUpdated="
        + brandsUpdated
        + ",productsUpdated="
        + productsUpdated
        + ",imagesUpdated="
        + imagesUpdated;
  }
}
