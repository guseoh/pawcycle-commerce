package com.pawcycle.backend.catalog.product.application;

import java.math.BigDecimal;
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
    Trust trust,
    List<SkuDetail> skus,
    boolean purchasable,
    BrandSummary brand,
    List<Image> images,
    List<OptionGroup> optionGroups) {

  public ProductDetailView(
      Long productId,
      String name,
      String petType,
      String description,
      String thumbnailUrl,
      CategorySummary category,
      List<SkuDetail> skus) {
    this(
        productId,
        name,
        null,
        petType,
        description,
        thumbnailUrl,
        category,
        List.of(),
        Trust.empty(),
        skus,
        skus.stream().anyMatch(SkuDetail::purchasable),
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
      Trust trust,
      List<SkuDetail> skus) {
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
        skus.stream().anyMatch(SkuDetail::purchasable),
        null,
        List.of(),
        List.of());
  }

  public ProductDetailView {
    detailSections = List.copyOf(detailSections);
    skus = List.copyOf(skus);
    trust = trust == null ? Trust.empty() : trust;
    images = List.copyOf(images == null ? List.of() : images);
    optionGroups = List.copyOf(optionGroups == null ? List.of() : optionGroups);
  }

  public record CategorySummary(Long categoryId, String name, String slug) {}

  public record BrandSummary(Long brandId, String name, String slug, String logoUrl) {}

  public record Image(
      Long imageId, String imageUrl, String altText, int displayOrder, String imageType) {}

  public record OptionGroup(
      Long optionGroupId, String name, int displayOrder, List<OptionValue> values) {
    public OptionGroup {
      values = List.copyOf(values);
    }
  }

  public record OptionValue(Long optionValueId, String value, int displayOrder) {}

  public record Trust(BigDecimal averageRating, long reviewCount, long questionCount) {
    public static Trust empty() {
      return new Trust(null, 0, 0);
    }
  }

  public record SkuDetail(
      Long skuId,
      String skuName,
      BigDecimal price,
      boolean subscribable,
      List<Integer> availableDeliveryCycles,
      int availableQuantity,
      boolean purchasable,
      BigDecimal compareAtPrice,
      Integer discountRate,
      List<SelectedOption> selectedOptions) {
    public SkuDetail(
        Long skuId,
        String skuName,
        BigDecimal price,
        boolean subscribable,
        List<Integer> availableDeliveryCycles,
        int availableQuantity,
        boolean purchasable) {
      this(
          skuId,
          skuName,
          price,
          subscribable,
          availableDeliveryCycles,
          availableQuantity,
          purchasable,
          null,
          null,
          List.of());
    }

    public SkuDetail(
        Long skuId,
        String skuName,
        BigDecimal price,
        boolean subscribable,
        List<Integer> availableDeliveryCycles) {
      this(
          skuId,
          skuName,
          price,
          subscribable,
          availableDeliveryCycles,
          0,
          true,
          null,
          null,
          List.of());
    }

    public SkuDetail {
      availableDeliveryCycles = List.copyOf(availableDeliveryCycles);
      selectedOptions = List.copyOf(selectedOptions == null ? List.of() : selectedOptions);
    }
  }

  public record SelectedOption(
      Long optionGroupId, String groupName, Long optionValueId, String value) {}
}
