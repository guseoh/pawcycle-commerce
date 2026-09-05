package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.CategoryFacetEntity;
import com.pawcycle.backend.catalog.admin.domain.FacetDefinitionEntity;
import com.pawcycle.backend.catalog.admin.domain.FacetOptionEntity;
import com.pawcycle.backend.catalog.admin.domain.ProductFacetValueEntity;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetAssignCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.CategoryFacetView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetDefinitionCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetDefinitionListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetDefinitionPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetDefinitionView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetOptionCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetOptionPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.FacetOptionView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ProductFacetValuesCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.ProductFacetValuesView;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CatalogFacetAdminPersistence {
  private final CategoryRepository categories;
  private final ProductRepository products;
  private final FacetDefinitionRepository definitions;
  private final FacetOptionRepository options;
  private final CategoryFacetRepository categoryFacets;
  private final ProductFacetValueRepository productFacets;
  private final ProductListCacheInvalidator cacheInvalidator;

  public CatalogFacetAdminPersistence(
      CategoryRepository categories,
      ProductRepository products,
      FacetDefinitionRepository definitions,
      FacetOptionRepository options,
      CategoryFacetRepository categoryFacets,
      ProductFacetValueRepository productFacets,
      ProductListCacheInvalidator cacheInvalidator) {
    this.categories = categories;
    this.products = products;
    this.definitions = definitions;
    this.options = options;
    this.categoryFacets = categoryFacets;
    this.productFacets = productFacets;
    this.cacheInvalidator = cacheInvalidator;
  }

  @Transactional(readOnly = true)
  public FacetDefinitionListView facetDefinitions() {
    return new FacetDefinitionListView(
        definitions.findAllByOrderByIdAsc().stream().map(this::toView).toList());
  }

  @Transactional(readOnly = true)
  public FacetDefinitionView facetDefinition(long definitionId) {
    return toView(requireDefinition(definitionId));
  }

  @Transactional
  public FacetDefinitionView createFacetDefinition(FacetDefinitionCreateCommand request) {
    try {
      return toView(
          definitions.saveAndFlush(
              new FacetDefinitionEntity(
                  CatalogAdminValidation.slug(request.key(), "key"), request.name())));
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict("FACET_KEY_CONFLICT", "이미 사용 중인 facet key입니다.");
    }
  }

  @Transactional
  public FacetDefinitionView updateFacetDefinition(
      long definitionId, FacetDefinitionPatchCommand request) {
    CatalogAdminValidation.requirePatch(request.keyPresent() || request.namePresent());
    FacetDefinitionEntity current = requireDefinition(definitionId);
    String key = request.keyPresent() ? CatalogAdminValidation.slug(request.key(), "key") : current.getKey();
    String name = request.namePresent() ? CatalogAdminValidation.requiredText(request.name(), "name", 100) : current.getName();
    try {
      current.update(key, name);
      definitions.flush();
      return toView(current);
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict("FACET_KEY_CONFLICT", "이미 사용 중인 facet key입니다.");
    }
  }

  @Transactional
  public void deleteFacetDefinition(long definitionId) {
    FacetDefinitionEntity current = requireDefinition(definitionId);
    try {
      definitions.delete(current);
      definitions.flush();
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict(
          "FACET_DEFINITION_IN_USE", "카테고리 또는 상품에 연결된 facet 정의는 삭제할 수 없습니다.");
    }
  }

  @Transactional
  public FacetOptionView createFacetOption(long definitionId, FacetOptionCreateCommand request) {
    FacetDefinitionEntity definition = requireDefinition(definitionId);
    try {
      return toView(
          options.saveAndFlush(
              new FacetOptionEntity(definition, request.value(), request.displayOrder())));
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict("FACET_OPTION_CONFLICT", "Facet 옵션 값은 중복될 수 없습니다.");
    }
  }

  @Transactional
  public FacetOptionView updateFacetOption(
      long definitionId, long optionId, FacetOptionPatchCommand request) {
    CatalogAdminValidation.requirePatch(request.valuePresent() || request.displayOrderPresent());
    FacetOptionEntity current = requireOption(definitionId, optionId);
    String value = request.valuePresent() ? CatalogAdminValidation.requiredText(request.value(), "value", 100) : current.getValue();
    int displayOrder = request.displayOrderPresent() ? CatalogAdminValidation.nonNegativeRequired(request.displayOrder(), "displayOrder") : current.getDisplayOrder();
    try {
      current.update(value, displayOrder);
      options.flush();
      return toView(current);
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict("FACET_OPTION_CONFLICT", "Facet 옵션 값은 중복될 수 없습니다.");
    }
  }

  @Transactional
  public void deleteFacetOption(long definitionId, long optionId) {
    FacetOptionEntity current = requireOption(definitionId, optionId);
    try {
      options.delete(current);
      options.flush();
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict("FACET_OPTION_IN_USE", "상품에 연결된 facet 옵션은 삭제할 수 없습니다.");
    }
  }

  @Transactional
  public CategoryFacetView assignCategoryFacet(
      long categoryId, long definitionId, CategoryFacetAssignCommand request) {
    Category category = requireCategory(categoryId);
    FacetDefinitionEntity definition = requireDefinition(definitionId);
    int displayOrder = CatalogAdminValidation.nonNegativeRequired(request.displayOrder(), "displayOrder");
    try {
      CategoryFacetEntity current =
          categoryFacets
              .findByIdCategoryIdAndIdFacetDefinitionId(categoryId, definitionId)
              .orElseGet(() -> new CategoryFacetEntity(category, definition, displayOrder));
      current.updateDisplayOrder(displayOrder);
      categoryFacets.saveAndFlush(current);
      return new CategoryFacetView(categoryId, definitionId, displayOrder);
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.validation("displayOrder", "0 이상이어야 합니다.");
    }
  }

  @Transactional
  public void removeCategoryFacet(long categoryId, long definitionId) {
    requireCategory(categoryId);
    products.findAllForUpdate();
    categoryFacets.findAllForUpdate(categoryId);
    CategoryFacetEntity current =
        categoryFacets
            .findByIdCategoryIdAndIdFacetDefinitionId(categoryId, definitionId)
            .orElseThrow(
                () ->
                    CatalogAdminValidation.missing(
                        "CATEGORY_FACET_NOT_FOUND", "카테고리 facet 배정을 확인할 수 없습니다."));
    if (productFacets.countByCategoryAndDefinition(categoryId, definitionId) > 0)
      throw CatalogAdminValidation.conflict(
          "CATEGORY_FACET_IN_USE", "상품이 사용 중인 facet 배정은 제거할 수 없습니다.");
    categoryFacets.delete(current);
    categoryFacets.flush();
  }

  @Transactional
  public ProductFacetValuesView setProductFacetValues(
      long productId, ProductFacetValuesCommand request) {
    Product product =
        products
            .findByIdForUpdate(productId)
            .orElseThrow(
                () -> CatalogAdminValidation.missing("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
    Long categoryId = product.getCategory().getId();
    Set<Long> allowedDefinitions =
        new HashSet<>(categoryFacets.findAllForUpdate(categoryId).stream()
            .map(facet -> facet.getFacetDefinition().getId())
            .toList());
    List<Long> ids = CatalogAdminValidation.distinct(request.facetOptionIds(), "facetOptionIds");
    List<FacetOptionEntity> selected = options.findAllById(ids);
    if (selected.size() != ids.size()
        || selected.stream().anyMatch(option -> !allowedDefinitions.contains(option.getFacetDefinition().getId())))
      throw CatalogAdminValidation.conflict(
          "PRODUCT_FACET_NOT_ALLOWED", "상품 카테고리에 허용되지 않은 facet 옵션입니다.");
    productFacets.deleteAllByProductId(productId);
    productFacets.saveAllAndFlush(selected.stream().map(option -> new ProductFacetValueEntity(product, option)).toList());
    cacheInvalidator.invalidateAfterCommit();
    return new ProductFacetValuesView(productId, ids);
  }

  @Transactional(readOnly = true)
  public ProductFacetValuesView productFacetValues(long productId) {
    requireProduct(productId);
    return new ProductFacetValuesView(productId, productFacets.findOptionIdsOrdered(productId));
  }

  @Transactional(readOnly = true)
  public CategoryFacetListView categoryFacets(long categoryId) {
    requireCategory(categoryId);
    return new CategoryFacetListView(
        categoryId,
        categoryFacets.findAllOrdered(categoryId).stream()
            .map(facet -> new CategoryFacetView(categoryId, facet.getFacetDefinition().getId(), facet.getDisplayOrder()))
            .toList());
  }

  private FacetDefinitionEntity requireDefinition(long id) {
    return definitions
        .findById(id)
        .orElseThrow(() -> CatalogAdminValidation.missing("FACET_DEFINITION_NOT_FOUND", "Facet 정의를 확인할 수 없습니다."));
  }

  private FacetOptionEntity requireOption(long definitionId, long optionId) {
    return options
        .findByFacetDefinition_IdAndId(definitionId, optionId)
        .orElseThrow(() -> CatalogAdminValidation.missing("FACET_OPTION_NOT_FOUND", "Facet 옵션을 확인할 수 없습니다."));
  }

  private Category requireCategory(long categoryId) {
    return categories
        .findById(categoryId)
        .orElseThrow(() -> CatalogAdminValidation.missing("CATEGORY_NOT_FOUND", "카테고리를 확인할 수 없습니다."));
  }

  private Product requireProduct(long productId) {
    return products
        .findById(productId)
        .orElseThrow(() -> CatalogAdminValidation.missing("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
  }

  private FacetDefinitionView toView(FacetDefinitionEntity definition) {
    return new FacetDefinitionView(
        definition.getId(),
        definition.getKey(),
        definition.getName(),
        options.findByFacetDefinition_IdOrderByDisplayOrderAscIdAsc(definition.getId()).stream()
            .map(this::toView)
            .toList());
  }

  private FacetOptionView toView(FacetOptionEntity option) {
    return new FacetOptionView(
        option.getId(),
        option.getFacetDefinition().getId(),
        option.getValue(),
        option.getDisplayOrder());
  }
}
