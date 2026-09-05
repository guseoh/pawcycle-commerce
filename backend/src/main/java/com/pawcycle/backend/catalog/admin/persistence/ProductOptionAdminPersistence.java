package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.ProductOptionGroupEntity;
import com.pawcycle.backend.catalog.admin.domain.ProductOptionValueEntity;
import com.pawcycle.backend.catalog.admin.domain.SkuOptionValueEntity;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionGroupCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionGroupListView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionGroupPatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionGroupView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionValueCreateCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionValuePatchCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.OptionValueView;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.SkuOptionValuesCommand;
import com.pawcycle.backend.catalog.admin.persistence.CatalogAdminModels.SkuOptionValuesView;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductOptionAdminPersistence {
  private final ProductRepository products;
  private final SkuRepository skus;
  private final ProductOptionGroupRepository groups;
  private final ProductOptionValueRepository values;
  private final SkuOptionValueRepository skuOptions;

  public ProductOptionAdminPersistence(
      ProductRepository products,
      SkuRepository skus,
      ProductOptionGroupRepository groups,
      ProductOptionValueRepository values,
      SkuOptionValueRepository skuOptions) {
    this.products = products;
    this.skus = skus;
    this.groups = groups;
    this.values = values;
    this.skuOptions = skuOptions;
  }

  @Transactional(readOnly = true)
  public OptionGroupListView optionGroups(long productId) {
    requireProduct(productId);
    return new OptionGroupListView(
        groups.findByProduct_IdOrderByDisplayOrderAscIdAsc(productId).stream()
            .map(group -> toView(group, values.findByOptionGroup_IdOrderByDisplayOrderAscIdAsc(group.getId())))
            .toList());
  }

  @Transactional
  public OptionGroupView createOptionGroup(long productId, OptionGroupCreateCommand request) {
    Product product = lockProduct(productId);
    if (groups.countByProduct_Id(productId) >= 2)
      throw CatalogAdminValidation.conflict(
          "OPTION_GROUP_LIMIT_EXCEEDED", "상품당 옵션 그룹은 최대 2개까지 지정할 수 있습니다.");
    try {
      return toView(groups.saveAndFlush(new ProductOptionGroupEntity(product, request.name(), request.displayOrder())), List.of());
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict(
          "OPTION_GROUP_NAME_CONFLICT", "상품 내 옵션 그룹 이름은 중복될 수 없습니다.");
    }
  }

  @Transactional
  public OptionGroupView updateOptionGroup(
      long productId, long groupId, OptionGroupPatchCommand request) {
    CatalogAdminValidation.requirePatch(request.namePresent() || request.displayOrderPresent());
    ProductOptionGroupEntity current = requireGroup(productId, groupId);
    String name = request.namePresent() ? CatalogAdminValidation.requiredText(request.name(), "name", 100) : current.getName();
    int displayOrder = request.displayOrderPresent() ? CatalogAdminValidation.nonNegativeRequired(request.displayOrder(), "displayOrder") : current.getDisplayOrder();
    try {
      current.update(name, displayOrder);
      groups.flush();
      return toView(current, values.findByOptionGroup_IdOrderByDisplayOrderAscIdAsc(groupId));
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict(
          "OPTION_GROUP_NAME_CONFLICT", "상품 내 옵션 그룹 이름은 중복될 수 없습니다.");
    }
  }

  @Transactional
  public void deleteOptionGroup(long productId, long groupId) {
    ProductOptionGroupEntity current = requireGroup(productId, groupId);
    try {
      groups.delete(current);
      groups.flush();
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict("OPTION_GROUP_IN_USE", "SKU에 연결된 옵션 그룹은 삭제할 수 없습니다.");
    }
  }

  @Transactional
  public OptionValueView createOptionValue(
      long productId, long groupId, OptionValueCreateCommand request) {
    ProductOptionGroupEntity group = requireGroup(productId, groupId);
    try {
      return toView(values.saveAndFlush(new ProductOptionValueEntity(group, request.value(), request.displayOrder())));
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict("OPTION_VALUE_CONFLICT", "옵션 그룹 내 값은 중복될 수 없습니다.");
    }
  }

  @Transactional
  public OptionValueView updateOptionValue(
      long productId, long groupId, long valueId, OptionValuePatchCommand request) {
    CatalogAdminValidation.requirePatch(request.valuePresent() || request.displayOrderPresent());
    ProductOptionValueEntity current = requireValue(productId, groupId, valueId);
    String value = request.valuePresent() ? CatalogAdminValidation.requiredText(request.value(), "value", 100) : current.getValue();
    int displayOrder = request.displayOrderPresent() ? CatalogAdminValidation.nonNegativeRequired(request.displayOrder(), "displayOrder") : current.getDisplayOrder();
    try {
      current.update(value, displayOrder);
      values.flush();
      return toView(current);
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict("OPTION_VALUE_CONFLICT", "옵션 그룹 내 값은 중복될 수 없습니다.");
    }
  }

  @Transactional
  public void deleteOptionValue(long productId, long groupId, long valueId) {
    ProductOptionValueEntity current = requireValue(productId, groupId, valueId);
    try {
      values.delete(current);
      values.flush();
    } catch (DataIntegrityViolationException exception) {
      throw CatalogAdminValidation.conflict("OPTION_VALUE_IN_USE", "SKU에 연결된 옵션 값은 삭제할 수 없습니다.");
    }
  }

  @Transactional
  public SkuOptionValuesView setSkuOptionValues(
      long productId, long skuId, SkuOptionValuesCommand request) {
    lockProduct(productId);
    Sku sku = requireSku(productId, skuId);
    List<Long> ids = CatalogAdminValidation.distinct(request.optionValueIds(), "optionValueIds");
    List<ProductOptionValueEntity> selected = values.findByOptionGroup_Product_IdAndIdIn(productId, ids);
    if (selected.size() != ids.size())
      throw CatalogAdminValidation.validation("optionValueIds", "상품에 속하지 않은 옵션 값입니다.");
    Set<Long> optionGroups = new HashSet<>();
    for (ProductOptionValueEntity value : selected) {
      if (!optionGroups.add(value.getOptionGroup().getId()))
        throw CatalogAdminValidation.conflict(
            "SKU_OPTION_GROUP_DUPLICATE", "SKU에는 그룹당 하나의 옵션 값만 지정할 수 있습니다.");
    }
    if (!ids.isEmpty() && hasSameCombination(productId, skuId, Set.copyOf(ids)))
      throw CatalogAdminValidation.conflict(
          "SKU_OPTION_COMBINATION_CONFLICT", "같은 옵션 조합의 SKU가 이미 있습니다.");
    skuOptions.deleteAllBySkuId(skuId);
    skuOptions.saveAllAndFlush(selected.stream().map(value -> new SkuOptionValueEntity(sku, value)).toList());
    return new SkuOptionValuesView(skuId, ids);
  }

  @Transactional(readOnly = true)
  public SkuOptionValuesView skuOptionValues(long productId, long skuId) {
    requireSku(productId, skuId);
    return new SkuOptionValuesView(skuId, skuOptions.findOptionValueIds(skuId));
  }

  private boolean hasSameCombination(long productId, long skuId, Set<Long> selected) {
    Map<Long, Set<Long>> combinations = new HashMap<>();
    for (SkuOptionValueEntity link : skuOptions.findBySku_Product_IdAndSku_IdNot(productId, skuId)) {
      combinations.computeIfAbsent(link.getSku().getId(), ignored -> new HashSet<>())
          .add(link.getOptionValue().getId());
    }
    return combinations.values().stream().anyMatch(existing -> existing.equals(selected));
  }

  private Product lockProduct(long productId) {
    return products
        .findByIdForUpdate(productId)
        .orElseThrow(() -> CatalogAdminValidation.missing("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
  }

  private Product requireProduct(long productId) {
    return products
        .findById(productId)
        .orElseThrow(() -> CatalogAdminValidation.missing("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
  }

  private Sku requireSku(long productId, long skuId) {
    return skus
        .findByIdAndProductId(skuId, productId)
        .orElseThrow(() -> CatalogAdminValidation.missing("SKU_NOT_FOUND", "SKU를 확인할 수 없습니다."));
  }

  private ProductOptionGroupEntity requireGroup(long productId, long groupId) {
    return groups
        .findByProduct_IdAndId(productId, groupId)
        .orElseThrow(() -> CatalogAdminValidation.missing("OPTION_GROUP_NOT_FOUND", "옵션 그룹을 확인할 수 없습니다."));
  }

  private ProductOptionValueEntity requireValue(long productId, long groupId, long valueId) {
    requireGroup(productId, groupId);
    return values
        .findByOptionGroup_IdAndId(groupId, valueId)
        .orElseThrow(() -> CatalogAdminValidation.missing("OPTION_VALUE_NOT_FOUND", "옵션 값을 확인할 수 없습니다."));
  }

  private OptionGroupView toView(ProductOptionGroupEntity group, List<ProductOptionValueEntity> values) {
    return new OptionGroupView(
        group.getId(), group.getProduct().getId(), group.getName(), group.getDisplayOrder(), values.stream().map(this::toView).toList());
  }

  private OptionValueView toView(ProductOptionValueEntity value) {
    return new OptionValueView(value.getId(), value.getOptionGroup().getId(), value.getValue(), value.getDisplayOrder());
  }
}
