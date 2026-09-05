package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.ProductImageEntity;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ImageCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ImageListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ImagePatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ImageView;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductImageAdminPersistence {
  private final ProductRepository products;
  private final ProductImageRepository images;
  private final ProductListCacheInvalidator cacheInvalidator;

  public ProductImageAdminPersistence(
      ProductRepository products,
      ProductImageRepository images,
      ProductListCacheInvalidator cacheInvalidator) {
    this.products = products;
    this.images = images;
    this.cacheInvalidator = cacheInvalidator;
  }

  @Transactional(readOnly = true)
  public ImageListView images(long productId) {
    requireProduct(productId);
    return new ImageListView(
        images.findByProduct_IdOrderByDisplayOrderAscIdAsc(productId).stream()
            .map(this::toView)
            .toList());
  }

  @Transactional
  public ImageView createImage(long productId, ImageCreateCommand request) {
    Product product = requireProduct(productId);
    try {
      ProductImageEntity image =
          images.saveAndFlush(
              new ProductImageEntity(
                  product,
                  request.imageUrl(),
                  request.altText(),
                  request.displayOrder(),
                  request.imageType()));
      cacheInvalidator.invalidateAfterCommit();
      return toView(image);
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict(
          "PRODUCT_MAIN_IMAGE_CONFLICT", "상품에는 MAIN 이미지를 하나만 지정할 수 있습니다.");
    }
  }

  @Transactional
  public ImageView updateImage(long productId, long imageId, ImagePatchCommand request) {
    CatalogAdminValidation.requirePatch(
        request.imageUrlPresent()
            || request.altTextPresent()
            || request.displayOrderPresent()
            || request.imageTypePresent());
    ProductImageEntity current = requireImage(productId, imageId);
    String imageUrl = request.imageUrlPresent() ? CatalogAdminValidation.requiredText(request.imageUrl(), "imageUrl", 2048) : current.getImageUrl();
    String altText = request.altTextPresent() ? CatalogAdminValidation.nullableText(request.altText(), "altText", 500) : current.getAltText();
    int displayOrder = request.displayOrderPresent() ? CatalogAdminValidation.nonNegativeRequired(request.displayOrder(), "displayOrder") : current.getDisplayOrder();
    String imageType = request.imageTypePresent() ? CatalogAdminValidation.imageType(request.imageType()) : current.getImageType();
    try {
      current.update(imageUrl, altText, displayOrder, imageType);
      images.flush();
      cacheInvalidator.invalidateAfterCommit();
      return toView(current);
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict(
          "PRODUCT_MAIN_IMAGE_CONFLICT", "상품에는 MAIN 이미지를 하나만 지정할 수 있습니다.");
    }
  }

  @Transactional
  public void deleteImage(long productId, long imageId) {
    ProductImageEntity current = requireImage(productId, imageId);
    images.delete(current);
    images.flush();
    cacheInvalidator.invalidateAfterCommit();
  }

  private Product requireProduct(long productId) {
    return products
        .findById(productId)
        .orElseThrow(() -> CatalogAdminValidation.missing("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
  }

  private ProductImageEntity requireImage(long productId, long imageId) {
    return images
        .findByProduct_IdAndId(productId, imageId)
        .orElseThrow(
            () -> CatalogAdminValidation.missing("PRODUCT_IMAGE_NOT_FOUND", "상품 이미지를 확인할 수 없습니다."));
  }

  private ImageView toView(ProductImageEntity image) {
    return new ImageView(
        image.getId(),
        image.getProduct().getId(),
        image.getImageUrl(),
        image.getAltText(),
        image.getDisplayOrder(),
        image.getImageType());
  }
}
