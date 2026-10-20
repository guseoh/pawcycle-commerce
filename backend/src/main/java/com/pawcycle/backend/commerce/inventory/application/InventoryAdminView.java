package com.pawcycle.backend.commerce.inventory.application;

/** Application projection; the HTTP response is constructed in the controller. */
public record InventoryAdminView(
    long skuId, int availableQuantity, int reservedQuantity, long version, String skuCode) {}
