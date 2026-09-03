package com.pawcycle.backend.catalog.product.application;

import java.util.List;

public record ProductDetailView(
    Long productId,
    String name,
    String shortDescription,
    String petType,
    String description,
    String thumbnailUrl,
    CategorySummary category,
    List<ProductDetailSectionView> detailSections,
    ProductTrust trust,
    List<ProductSkuDetail> skus,
    boolean purchasable,
    BrandSummary brand,
    List<ProductImage> images,
    List<ProductOptionGroup> optionGroups) {

  public ProductDetailView(
      Long productId,
      String name,
      String petType,
      String description,
      String thumbnailUrl,
      CategorySummary category,
      List<ProductSkuDetail> skus) {
    this(
        productId,
        name,
        null,
        petType,
        description,
        thumbnailUrl,
        category,
        List.of(),
        ProductTrust.empty(),
        skus,
        skus.stream().anyMatch(ProductSkuDetail::purchasable),
        null,
        List.of(),
        List.of());
  }

  public ProductDetailView(
      Long productId,
      String name,
      String shortDescription,
      String petType,
      String description,
      String thumbnailUrl,
      CategorySummary category,
      List<ProductDetailSectionView> detailSections,
      ProductTrust trust,
      List<ProductSkuDetail> skus) {
    this(
        productId,
        name,
        shortDescription,
        petType,
        description,
        thumbnailUrl,
        category,
        detailSections,
        trust,
        skus,
        skus.stream().anyMatch(ProductSkuDetail::purchasable),
        null,
        List.of(),
        List.of());
  }

  public ProductDetailView {
    detailSections = List.copyOf(detailSections);
    skus = List.copyOf(skus);
    trust = trust == null ? ProductTrust.empty() : trust;
    images = List.copyOf(images == null ? List.of() : images);
    optionGroups = List.copyOf(optionGroups == null ? List.of() : optionGroups);
  }

}
