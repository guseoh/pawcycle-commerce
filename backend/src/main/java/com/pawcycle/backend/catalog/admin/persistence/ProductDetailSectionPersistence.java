package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogNotFoundException;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogValidationException;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.DetailSectionCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.DetailSectionListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.DetailSectionPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.DetailSectionView;
import com.pawcycle.backend.catalog.product.domain.ProductDetailSectionEntity;
import com.pawcycle.backend.catalog.product.persistence.ProductDetailSectionRepository;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductDetailSectionPersistence {
  private final ProductDetailSectionRepository sections;
  private final ProductRepository products;
  private final Clock clock;

  public ProductDetailSectionPersistence(
      ProductDetailSectionRepository sections, ProductRepository products, Clock clock) {
    this.sections = sections;
    this.products = products;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public DetailSectionListView list(long productId) {
    requireProduct(productId);
    return new DetailSectionListView(
        sections.findAllByProductId(productId).stream().map(this::view).toList());
  }

  @Transactional
  public DetailSectionView create(long productId, DetailSectionCreateCommand request) {
    requireProduct(productId);
    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    return view(
        sections.saveAndFlush(
            new ProductDetailSectionEntity(
                productId,
                request.title(),
                request.body(),
                request.displayOrder(),
                request.visible(),
                now,
                now)));
  }

  @Transactional
  public DetailSectionView update(
      long productId, long sectionId, DetailSectionPatchCommand request) {
    validate(request);
    requireProduct(productId);
    ProductDetailSectionEntity current =
        sections
            .findByIdAndProductIdForUpdate(sectionId, productId)
            .orElseThrow(() -> notFound(sectionId));
    String title = request.titlePresent() ? request.title() : current.getTitle();
    String body = request.bodyPresent() ? request.body() : current.getBody();
    int displayOrder = request.displayOrderPresent() ? request.displayOrder() : current.getDisplayOrder();
    boolean visible = request.visiblePresent() ? request.visible() : current.isVisible();
    current.update(
        title,
        body,
        displayOrder,
        visible,
        LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()));
    return view(sections.saveAndFlush(current));
  }

  @Transactional
  public void delete(long productId, long sectionId) {
    requireProduct(productId);
    ProductDetailSectionEntity current =
        sections
            .findByIdAndProductIdForUpdate(sectionId, productId)
            .orElseThrow(() -> notFound(sectionId));
    sections.delete(current);
    sections.flush();
  }

  private void requireProduct(long productId) {
    if (!products.existsById(productId))
      throw new AdminCatalogNotFoundException("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다.");
  }

  private AdminCatalogNotFoundException notFound(long sectionId) {
    return new AdminCatalogNotFoundException("DETAIL_SECTION_NOT_FOUND", "상품 상세 섹션을 확인할 수 없습니다.");
  }

  private void validate(DetailSectionPatchCommand request) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    if (!request.titlePresent() && !request.bodyPresent() && !request.displayOrderPresent() && !request.visiblePresent())
      errors.add(new FieldErrorResponse("request", "수정할 필드를 하나 이상 입력해 주세요."));
    if (request.titlePresent() && (request.title() == null || request.title().isBlank() || request.title().length() > 200))
      errors.add(new FieldErrorResponse("title", "필수 입력이며 200자 이하여야 합니다."));
    if (request.bodyPresent() && (request.body() == null || request.body().isBlank() || request.body().length() > 10000))
      errors.add(new FieldErrorResponse("body", "필수 입력이며 10000자 이하여야 합니다."));
    if (request.displayOrderPresent() && (request.displayOrder() == null || request.displayOrder() < 0))
      errors.add(new FieldErrorResponse("displayOrder", "0 이상이어야 합니다."));
    if (request.visiblePresent() && request.visible() == null)
      errors.add(new FieldErrorResponse("visible", "필수 입력입니다."));
    if (!errors.isEmpty()) throw new AdminCatalogValidationException(errors);
  }

  private DetailSectionView view(ProductDetailSectionEntity section) {
    return new DetailSectionView(
        section.getId(),
        section.getProductId(),
        section.getTitle(),
        section.getBody(),
        section.getDisplayOrder(),
        section.isVisible(),
        instant(section.getCreatedAt()),
        instant(section.getUpdatedAt()));
  }

  private Instant instant(LocalDateTime value) {
    return value.atZone(ZoneId.systemDefault()).toInstant();
  }
}
