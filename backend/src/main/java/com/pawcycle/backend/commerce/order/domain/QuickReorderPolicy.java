package com.pawcycle.backend.commerce.order.domain;

/** Pure decision over the already locked reorder snapshot; performs no persistence or clock reads. */
public final class QuickReorderPolicy {
  private QuickReorderPolicy() {}

  public static String skipReason(String skuStatus, String productStatus, boolean categoryActive,
      Integer availableQuantity, int quantity) {
    if (!"ACTIVE".equals(skuStatus) || !"PUBLIC".equals(productStatus) || !categoryActive)
      return "SKU_NOT_PURCHASABLE";
    if (availableQuantity == null || availableQuantity < quantity) return "OUT_OF_STOCK";
    return null;
  }
}
