import assert from "node:assert/strict";
import test from "node:test";
import { categoryHierarchy, categoryParents, dirtyPayload, dirtyFormPayload, formValues, normalizeMoney, optionAssignment, parseForm, productBrandChoices, productCategoryChoices, productStatusAction, type CatalogField } from "./admin-catalog-forms.ts";
import type { Category, OptionGroup, SkuInput } from "./admin-catalog-api.ts";

test("PATCH sends changed fields only, including false and zero", () => {
  const baseline = { name: "기존", active: true, displayOrder: 2 };
  assert.deepEqual(dirtyPayload(baseline, { name: "기존", active: false, displayOrder: 0 }), { active: false, displayOrder: 0 });
  assert.deepEqual(dirtyPayload(baseline, { name: undefined }), {});
});

test("only explicitly nullable fields can be cleared", () => {
  const baseline = { parentId: 1 as number | null, name: "이름" as string | null };
  assert.deepEqual(dirtyPayload(baseline, { parentId: null }, ["parentId"]), { parentId: null });
  assert.throws(() => dirtyPayload(baseline, { name: null }, ["parentId"]), /해제할 수 없습니다/);
});

test("unchanged nullable empty text is omitted, while actual clearing sends null", () => {
  const fields: CatalogField<{ name: string; logoUrl: string | null }>[] = [{ key: "name", label: "이름", required: true }, { key: "logoUrl", label: "로고", nullable: true }];
  const before = { name: "기존", logoUrl: "" };
  const values = { ...formValues(before, fields), name: "변경" };
  assert.deepEqual(dirtyFormPayload(before, values, parseForm(values, fields, true).value, fields), { name: "변경" });
  const populated = { name: "기존", logoUrl: "https://example.com/logo.png" };
  const cleared = { ...formValues(populated, fields), logoUrl: "" };
  assert.deepEqual(dirtyFormPayload(populated, cleared, parseForm(cleared, fields, true).value, fields), { logoUrl: null });
});

test("Product actions follow the three allowed transitions", () => {
  assert.equal(productStatusAction("DRAFT").status, "PUBLIC");
  assert.equal(productStatusAction("PUBLIC").status, "INACTIVE");
  assert.equal(productStatusAction("INACTIVE").status, "PUBLIC");
});

const categories: Category[] = [
  { categoryId: 3, parentId: 1, name: "자식", slug: "child", displayOrder: 0, active: true },
  { categoryId: 2, parentId: null, name: "둘째", slug: "second", displayOrder: 2, active: false },
  { categoryId: 1, parentId: null, name: "부모", slug: "parent", displayOrder: 1, active: true },
];
test("Category hierarchy is two-depth and retains inactive rows", () => {
  assert.deepEqual(categoryHierarchy(categories).map((r) => [r.category.categoryId, r.depth, r.label]), [[1, 0, "부모"], [3, 1, "부모 / 자식"], [2, 0, "둘째"]]);
});
test("Category parent choices exclude self, children and nesting a parent", () => {
  assert.deepEqual(categoryParents(categories, 1), []);
  assert.deepEqual(categoryParents(categories, 3).map((c) => c.categoryId), [2, 1]);
  assert.deepEqual(categoryParents(categories, 2).map((c) => c.categoryId), [1]);
});

test("Product category and brand choices disable server-rejected assignments", () => {
  const systemCategory: Category = { categoryId: 99, parentId: null, name: "시스템", slug: "__pawcycle_uncategorized__", displayOrder: 99, active: true };
  const categoryChoices = productCategoryChoices([...categories, systemCategory]);
  assert.equal(categoryChoices.find((choice) => choice.value === "1")?.disabled, undefined);
  assert.equal(categoryChoices.find((choice) => choice.value === "2")?.disabled, true);
  assert.match(categoryChoices.find((choice) => choice.value === "2")?.label ?? "", /비활성/);
  assert.equal(categoryChoices.find((choice) => choice.value === "99")?.disabled, true);
  assert.match(categoryChoices.find((choice) => choice.value === "99")?.label ?? "", /시스템/);

  const brandChoices = productBrandChoices([
    { brandId: 1, name: "활성 브랜드", slug: "active-brand", logoUrl: null, active: true, displayOrder: 0 },
    { brandId: 2, name: "비활성 브랜드", slug: "inactive-brand", logoUrl: null, active: false, displayOrder: 1 },
  ]);
  assert.equal(brandChoices.find((choice) => choice.value === "1")?.disabled, undefined);
  assert.equal(brandChoices.find((choice) => choice.value === "2")?.disabled, true);
  assert.match(brandChoices.find((choice) => choice.value === "2")?.label ?? "", /비활성/);
});

test("SKU compareAtPrice normalization preserves zero, clears blank and rejects invalid decimals", () => {
  assert.equal(normalizeMoney("19900.00", true), 19900);
  assert.equal(normalizeMoney("  ", true), null);
  assert.equal(normalizeMoney("0", true), 0);
  for (const value of ["", "-1", "1.001", "NaN", "1e3", "10000000000"]) assert.throws(() => normalizeMoney(value));
});

test("SKU form omits immutable code and equivalent prices from PATCH", () => {
  const before: SkuInput = { skuCode: "SKU-1", name: "옵션", price: 100, compareAtPrice: 200, subscribable: false, displayOrder: 0, status: "ACTIVE" };
  const fields: CatalogField<SkuInput>[] = [{ key: "skuCode", label: "코드", readOnlyOnEdit: true }, { key: "price", label: "가격", kind: "money" }, { key: "compareAtPrice", label: "기준가", kind: "money", nullable: true }];
  const raw = { skuCode: "TAMPERED", price: "100.00", compareAtPrice: "" };
  const parsed = parseForm(raw, fields, true);
  assert.deepEqual(parsed.errors, []);
  assert.deepEqual(dirtyFormPayload(before, raw, parsed.value, fields), { compareAtPrice: null });
});

test("form validation reports fields and does not silently coerce invalid integers", () => {
  const fields: CatalogField<{ name: string; displayOrder: number }>[] = [{ key: "name", label: "이름", required: true }, { key: "displayOrder", label: "순서", kind: "number" }];
  assert.deepEqual(parseForm({ name: " ", displayOrder: "1.2" }, fields, false).errors.map((e) => e.field), ["name", "displayOrder"]);
});

const groups: OptionGroup[] = [
  { optionGroupId: 1, productId: 10, name: "크기", displayOrder: 0, values: [{ optionValueId: 11, optionGroupId: 1, value: "S", displayOrder: 0 }] },
  { optionGroupId: 2, productId: 10, name: "색상", displayOrder: 1, values: [{ optionValueId: 21, optionGroupId: 2, value: "녹색", displayOrder: 0 }] },
];
test("SKU option assignment yields one value per group and supports clearing", () => {
  assert.deepEqual(optionAssignment(groups, { 1: "11", 2: "21" }), [11, 21]);
  assert.deepEqual(optionAssignment(groups, { 1: "", 2: "" }), []);
  assert.throws(() => optionAssignment(groups, { 1: "21" }), /현재 상품/);
});
