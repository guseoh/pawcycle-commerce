import type { ProductOptionGroup, ProductSku } from "./api.ts";

export type OptionSelection = Record<number, number>;

// Match server combinations only; never infer availability from stock or price.
export function selectProductSku(groups: ProductOptionGroup[], skus: ProductSku[], selection: OptionSelection, legacySkuId: number | null = null): ProductSku | null {
  if (groups.length === 0) return skus.find((sku) => sku.skuId === legacySkuId) ?? (legacySkuId === null && skus.length === 1 ? skus[0] : null);
  if (Object.keys(selection).length !== groups.length || groups.some((group) => !group.values.some((value) => value.optionValueId === selection[group.optionGroupId]))) return null;
  return skus.find((sku) => sku.selectedOptions.length === groups.length &&
    groups.every((group) => sku.selectedOptions.some((option) => option.optionGroupId === group.optionGroupId && option.optionValueId === selection[group.optionGroupId]))) ?? null;
}

export function productQuantityError(quantity: string, sku: ProductSku | null): string | null {
  if (!sku?.purchasable) return null;
  const value = Number(quantity);
  if (!Number.isInteger(value) || value < 1) return "수량은 1 이상의 정수여야 합니다.";
  if (value > sku.availableQuantity) return `현재 재고 ${sku.availableQuantity}개 이하로 선택해 주세요.`;
  return null;
}
