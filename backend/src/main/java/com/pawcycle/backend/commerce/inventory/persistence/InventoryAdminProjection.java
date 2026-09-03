package com.pawcycle.backend.commerce.inventory.persistence;

public interface InventoryAdminProjection {
  Long getSkuId();

  Integer getAvailableQuantity();

  Integer getReservedQuantity();

  Long getVersion();

  String getSkuCode();
}