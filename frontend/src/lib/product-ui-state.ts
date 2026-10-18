import type { ProductDetail, ProductSku } from "./api.ts";
import { productQuantityError, selectProductSku, type OptionSelection } from "./product-selection.ts";

type ProductOptions = Pick<ProductDetail, "productId" | "optionGroups" | "skus">;
export interface ProductUiDraft { selection: OptionSelection; skuId: number | null; quantity: string }
const key = (id: number) => "pawcycle-product-draft-v1:" + id;

// Selection is local UI only. Purchasability always comes from the fresh server flag.
export function validateProductDraft(product: ProductOptions, draft: unknown): ProductUiDraft {
  const empty: ProductUiDraft = { selection: {}, skuId: null, quantity: "1" };
  const value = draft && typeof draft === "object" ? draft as Partial<ProductUiDraft> : {};
  const candidate = value.selection && typeof value.selection === "object" && !Array.isArray(value.selection) ? value.selection : {};
  const selection: OptionSelection = {};
  for (const group of product.optionGroups) {
    const id = candidate[group.optionGroupId];
    if (group.values.some((option) => option.optionValueId === id)) selection[group.optionGroupId] = id;
  }
  const legacyId = typeof value.skuId === "number" ? value.skuId : null;
  let sku = selectProductSku(product.optionGroups, product.skus, selection, legacyId);
  if (!sku?.purchasable) {
    sku = product.skus.length === 1 && product.skus[0].purchasable ? product.skus[0] : null;
    if (!sku) return empty;
    for (const option of sku.selectedOptions) selection[option.optionGroupId] = option.optionValueId;
    if (selectProductSku(product.optionGroups, product.skus, selection, sku.skuId)?.skuId !== sku.skuId) return empty;
  }
  const quantity = typeof value.quantity === "string" && productQuantityError(value.quantity, sku) === null ? value.quantity : "1";
  return { selection, skuId: sku.skuId, quantity };
}

export function readProductDraft(product: ProductOptions): ProductUiDraft {
  try { return validateProductDraft(product, typeof window === "undefined" ? null : JSON.parse(window.sessionStorage.getItem(key(product.productId)) ?? "null")); }
  catch { return validateProductDraft(product, null); }
}

export function rememberProductDraft(productId: number, draft: ProductUiDraft): void {
  if (typeof window === "undefined") return;
  try { window.sessionStorage.setItem(key(productId), JSON.stringify({ selection: draft.selection, skuId: draft.skuId, quantity: draft.quantity })); } catch { /* Storage can be unavailable or full. */ }
}

// Range only summarizes supplied server SKU prices; it never predicts a discount or availability.
export function productPriceRange(skus: Pick<ProductSku, "price">[]): { min: number; max: number } | null {
  const prices = skus.map((sku) => sku.price).filter((price) => Number.isFinite(price) && price >= 0);
  return prices.length ? { min: Math.min(...prices), max: Math.max(...prices) } : null;
}
