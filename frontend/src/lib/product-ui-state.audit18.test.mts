import assert from "node:assert/strict";
import test from "node:test";
import type { ProductDetail, ProductSku } from "./api.ts";
import { productPriceRange, readProductDraft, rememberProductDraft, validateProductDraft } from "./product-ui-state.ts";

const sku = (skuId: number, purchasable = true, availableQuantity = 3): ProductSku => ({ skuId, skuName: "옵션", price: 1000, compareAtPrice: null, discountRate: null, selectedOptions: [], subscribable: false, availableDeliveryCycles: [], availableQuantity, purchasable });
const product = (skus: ProductSku[], optionGroups: ProductDetail["optionGroups"] = []) => ({ productId: 18, skus, optionGroups });

test("audit18 auto-selects only a sole server-purchasable SKU, never stock-derived or multi-SKU", () => {
  assert.equal(validateProductDraft(product([sku(1)]), null).skuId, 1);
  assert.equal(validateProductDraft(product([sku(1, false, 99)]), null).skuId, null);
  assert.equal(validateProductDraft(product([sku(1), sku(2, false)]), null).skuId, null);
  assert.equal(validateProductDraft(product([sku(1, true, 0)]), null).skuId, 1);
});

test("audit18 restores valid combinations and validates fresh quantity/availability", () => {
  const draft = { selection: {}, skuId: 2, quantity: "2", replay: "cart" };
  assert.deepEqual(validateProductDraft(product([sku(1), sku(2)]), draft), { selection: {}, skuId: 2, quantity: "2" });
  for (const quantity of ["0", "3.5", "999", "bad"]) assert.equal(validateProductDraft(product([sku(1), sku(2)]), { ...draft, quantity }).quantity, "1");
  assert.equal(validateProductDraft(product([sku(1), sku(2, false)]), draft).skuId, null);
  assert.equal(validateProductDraft(product([sku(1), sku(3)]), draft).skuId, null);
});

test("audit18 grouped sole SKU selects only server-backed option IDs", () => {
  const groups = [{ optionGroupId: 1, name: "크기", displayOrder: 0, values: [{ optionValueId: 11, value: "소", displayOrder: 0 }] }];
  const grouped = { ...sku(1), selectedOptions: [{ optionGroupId: 1, optionValueId: 11, groupName: "크기", value: "소" }] };
  assert.deepEqual(validateProductDraft(product([grouped], groups), null).selection, { 1: 11 });
  assert.equal(validateProductDraft(product([sku(1)], groups), null).skuId, null);
});

test("audit18 price range includes server prices regardless of purchasability", () => {
  assert.deepEqual(productPriceRange([{ price: 2000 }, { price: 1000 }]), { min: 1000, max: 2000 });
  assert.deepEqual(productPriceRange([{ price: 0 }]), { min: 0, max: 0 });
  assert.equal(productPriceRange([]), null);
});

test("audit18 storage is SSR safe and never schedules mutation replay", () => {
  assert.equal(readProductDraft(product([sku(1)])).skuId, 1);
  assert.doesNotThrow(() => rememberProductDraft(18, { selection: {}, skuId: 1, quantity: "2" }));
});
