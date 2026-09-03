package com.pawcycle.backend.subscription;

import java.math.BigDecimal;

public record ScheduleAddonProjection(
    long scheduleId,
    long skuId,
    long productId,
    String productName,
    String skuName,
    int quantity,
    BigDecimal unitPriceKrw) {
  public BigDecimal lineAmount() {
    return unitPriceKrw.multiply(BigDecimal.valueOf(quantity));
  }
}
