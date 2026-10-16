package com.pawcycle.backend.commerce.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuickReorderPolicyTests {
  @ParameterizedTest
  @CsvSource(value = {
      "ACTIVE,PUBLIC,true,3,3,OK",
      "ACTIVE,PUBLIC,true,2,3,OUT_OF_STOCK",
      "ACTIVE,PUBLIC,true,NULL,1,OUT_OF_STOCK",
      "INACTIVE,PUBLIC,true,NULL,1,SKU_NOT_PURCHASABLE",
      "ACTIVE,INACTIVE,true,0,1,SKU_NOT_PURCHASABLE",
      "ACTIVE,PUBLIC,false,0,1,SKU_NOT_PURCHASABLE",
      "NULL,PUBLIC,true,9,1,SKU_NOT_PURCHASABLE",
      "ACTIVE,NULL,true,9,1,SKU_NOT_PURCHASABLE",
      "active,PUBLIC,true,9,1,SKU_NOT_PURCHASABLE",
      "ACTIVE,PUBLIC,true,0,0,OK",
      "ACTIVE,PUBLIC,true,-1,1,OUT_OF_STOCK",
      "ACTIVE,PUBLIC,true,2147483647,2147483647,OK"
  }, nullValues = "NULL")
  void preservesStatusPriorityNullStockAndExactQuantityBoundary(String sku, String product,
      boolean category, Integer available, int quantity, String expected) {
    assertThat(QuickReorderPolicy.skipReason(sku, product, category, available, quantity))
        .isEqualTo("OK".equals(expected) ? null : expected);
  }
}
