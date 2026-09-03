package com.pawcycle.backend.commerce.inventory.api;

public record InventoryResponse(
    long skuId,
    int availableQuantity,
    int reservedQuantity,
    long version,
    String skuCode) {}