package com.pawcycle.backend.catalog.application;

public record DemoCatalogImportPostflight(
    long expectedCategories,
    long actualCategories,
    long expectedProducts,
    long actualProducts,
    long expectedSkus,
    long actualSkus,
    long expectedInventories,
    long actualInventories,
    long expectedPlans,
    long actualPlans,
    long expectedPlanVersions,
    long actualPlanVersions,
    long expectedPlanItems,
    long actualPlanItems,
    long expectedDeliveryCycles,
    long actualDeliveryCycles) {

  public boolean complete() {
    return expectedCategories == actualCategories
        && expectedProducts == actualProducts
        && expectedSkus == actualSkus
        && expectedInventories == actualInventories
        && expectedPlans == actualPlans
        && expectedPlanVersions == actualPlanVersions
        && expectedPlanItems == actualPlanItems
        && expectedDeliveryCycles == actualDeliveryCycles;
  }

  public String summary() {
    return "postflight="
        + (complete() ? "PASS" : "FAIL")
        + " catalog_counts="
        + actualCategories
        + "/"
        + actualProducts
        + "/"
        + actualSkus
        + " inventory_count="
        + actualInventories
        + " plan_counts="
        + actualPlans
        + "/"
        + actualPlanVersions
        + "/"
        + actualPlanItems
        + "/"
        + actualDeliveryCycles;
  }
}
