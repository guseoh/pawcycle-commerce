package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;

public record ProductSummary(
    Long productId,
    String name,
    String petType,
    String shortDescription,
    String thumbnailUrl,
    CategorySummary category,
    SkuPriceSummary skuPriceSummary,
    boolean hasSubscribableSku,
    BigDecimal representativePrice,
    boolean purchasable,
    BrandSummary brand,
    BigDecimal compareAtPrice,
    Integer discountRate,
    BigDecimal averageRating,
    long reviewCount) {

  public ProductSummary(
      Long productId,
      String name,
      String petType,
      String shortDescription,
      String thumbnailUrl,
      CategorySummary category,
      SkuPriceSummary skuPriceSummary,
      boolean hasSubscribableSku) {
    this(
        productId,
        name,
        petType,
        shortDescription,
        thumbnailUrl,
        category,
        skuPriceSummary,
        hasSubscribableSku,
        skuPriceSummary.skuPrices().isEmpty() ? null : skuPriceSummary.skuPrices().getFirst().price(),
        true,
        null,
        null,
        null,
        null,
        0);
  }

  public ProductSummary(
      Long productId,
      String name,
      String petType,
      String shortDescription,
      String thumbnailUrl,
      CategorySummary category,
      SkuPriceSummary skuPriceSummary,
      boolean hasSubscribableSku,
      BigDecimal representativePrice,
      boolean purchasable) {
    this(
        productId,
        name,
        petType,
        shortDescription,
        thumbnailUrl,
        category,
        skuPriceSummary,
        hasSubscribableSku,
        representativePrice,
        purchasable,
        null,
        null,
        null,
        null,
        0);
  }
}
