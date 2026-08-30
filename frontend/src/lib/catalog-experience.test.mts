import assert from "node:assert/strict";
import test from "node:test";
import { catalogDiscoveryApi, productApi, type CatalogDiscovery, type ProductDetail, type ProductOptionGroup, type ProductSku, type ProductSummary } from "./api.ts";
import { catalogHref, catalogMetadata, catalogPriceRangeError, catalogQuery, changeCatalogFilters, parseCatalogFilters, PRODUCT_SORTS } from "./catalog-filters.ts";
import { productQuantityError, selectProductSku } from "./product-selection.ts";
import { reviewCollectionCopy } from "./review-collection-copy.ts";
import { loadProductResults } from "./catalog-products.ts";
import { currentProductWishlist, loadProductWishlist, type ProductWishlistState } from "./product-wishlist.ts";
import { commerceFinalApi } from "./commerce-final-api.ts";

const discovery: CatalogDiscovery = {
  categories: [{ categoryId: 1, name: "사료", slug: "food", displayOrder: 0, children: [{ categoryId: 10, name: "건식", slug: "food-dry", displayOrder: 0 }] }, { categoryId: 2, name: "용품", slug: "supplies", displayOrder: 1, children: [] }],
  brands: [{ brandId: 1, name: "브랜드", slug: "brand", logoUrl: null, displayOrder: 0 }],
  categoryFacets: [{ categorySlug: "food", facets: [{ key: "age", name: "연령", displayOrder: 0, options: [{ optionId: 1, value: "성묘", displayOrder: 0 }] }] }, { categorySlug: "food-dry", facets: [{ key: "protein", name: "주원료", displayOrder: 0, options: [{ optionId: 2, value: "연어", displayOrder: 0 }] }] }],
};
const groups: ProductOptionGroup[] = [
  { optionGroupId: 10, name: "용량", displayOrder: 0, values: [{ optionValueId: 11, value: "소", displayOrder: 0 }, { optionValueId: 12, value: "대", displayOrder: 1 }] },
  { optionGroupId: 20, name: "팩 수", displayOrder: 1, values: [{ optionValueId: 21, value: "1팩", displayOrder: 0 }, { optionValueId: 22, value: "3팩", displayOrder: 1 }] },
];
function sku(id: number, values: number[], overrides: Partial<ProductSku> = {}): ProductSku {
  return { skuId: id, skuName: `옵션 ${id}`, price: 12700, compareAtPrice: 15000, discountRate: 15, purchasable: true, subscribable: true, availableQuantity: 7, availableDeliveryCycles: [2, 4], selectedOptions: values.map((value, index) => ({ optionGroupId: groups[index].optionGroupId, groupName: groups[index].name, optionValueId: value, value: groups[index].values.find((option) => option.optionValueId === value)!.value })), ...overrides };
}
const skus = [sku(1, [11, 21]), sku(2, [12, 22], { price: 30123, compareAtPrice: null, discountRate: null, availableQuantity: 3, purchasable: false, subscribable: false })];
const summary: ProductSummary = { productId: 4, name: "고양이 사료", petType: "CAT", shortDescription: "매일의 식사", thumbnailUrl: null, category: discovery.categories[0].children[0], brand: discovery.brands[0], skuPriceSummary: { skuPrices: [] }, hasSubscribableSku: true, representativePrice: 12700, compareAtPrice: 15000, discountRate: 15, averageRating: 4.7, reviewCount: 9, purchasable: true };
const detail: ProductDetail = { productId: 4, name: summary.name, shortDescription: summary.shortDescription, petType: "CAT", description: "설명", thumbnailUrl: null, category: summary.category, brand: summary.brand, images: [{ imageId: 5, imageUrl: "/photo.jpg", altText: "상품 포장", displayOrder: 0, imageType: "DETAIL" }], optionGroups: groups, skus, purchasable: true, detailSections: [], trust: { averageRating: 4.7, reviewCount: 9, questionCount: 0 } };

test("Catalog clients consume V24 JSON without computing prices, ratings or selection", async () => {
  const original = globalThis.fetch;
  const requests: string[] = [];
  const bodies = [discovery, { items: [summary], page: 0, size: 12, totalElements: 1, totalPages: 1 }, detail];
  globalThis.fetch = (async (input, init) => {
    requests.push(String(input));
    assert.equal(init?.credentials, "same-origin");
    return new Response(JSON.stringify(bodies.shift()), { status: 200 });
  }) as typeof fetch;
  try {
    assert.deepEqual(await catalogDiscoveryApi.get(), discovery);
    assert.deepEqual((await productApi.list()).items[0], summary);
    assert.deepEqual(await productApi.detail("4"), detail);
    assert.deepEqual(requests, ["/api/catalog/discovery", "/api/products", "/api/products/4"]);
  } finally { globalThis.fetch = original; }
});

test("Product filters send repeated facets and every approved query field", async () => {
  const original = globalThis.fetch;
  let path = "";
  globalThis.fetch = (async (input) => { path = String(input); return new Response("{}"); }) as typeof fetch;
  const filters = { q: "연어 사료", petType: "CAT", category: "food", subcategory: "food-dry", brand: "brand", facet: ["protein:연어", "age:성묘"], minPrice: 0, maxPrice: 50000, subscribable: true, purchasable: false, page: 2, size: 6, sort: "RATING" as const };
  try {
    await productApi.list(filters);
    const query = new URL(path, "http://localhost").searchParams;
    assert.deepEqual(query.getAll("facet"), filters.facet);
    for (const [key, value] of Object.entries(filters)) if (key !== "facet") assert.equal(query.get(key), String(value));
  } finally { globalThis.fetch = original; }
});

test("URL filters round-trip all fields and preserve multiple facets through unrelated edits", () => {
  const filters = parseCatalogFilters(new URLSearchParams("q=food&petType=DOG&category=food&subcategory=food-dry&brand=brand&facet=protein:연어&facet=protein:닭&minPrice=0&maxPrice=30000&subscribable=true&purchasable=false&page=3&size=6&sort=REVIEW_COUNT"));
  assert.deepEqual(parseCatalogFilters(new URLSearchParams(catalogQuery(filters))), filters);
  const changed = changeCatalogFilters(filters, { sort: "PRICE_ASC" });
  assert.equal(changed.page, 0);
  assert.deepEqual(changed.facet, ["protein:연어", "protein:닭"]);
  assert.equal(changed.subcategory, "food-dry");
  assert.equal(new URL(catalogHref(changed), "http://localhost").searchParams.has("page"), false);
});

test("Changing category clears dependent child and facets, changing child only clears facets", () => {
  const filters = { category: "food", subcategory: "food-dry", facet: ["protein:연어"], brand: "brand", page: 4 };
  assert.deepEqual(changeCatalogFilters(filters, { category: "supplies" }), { category: "supplies", subcategory: undefined, facet: [], brand: "brand", page: 0 });
  assert.deepEqual(changeCatalogFilters(filters, { subcategory: undefined }).facet, []);
  assert.equal(changeCatalogFilters(filters, { q: "new" }).page, 0);
});

test("Metadata shows only direct children and the selected category's facets", () => {
  assert.deepEqual(catalogMetadata(discovery, {}).facets, []);
  assert.deepEqual(catalogMetadata(discovery, { category: "food" }).children, discovery.categories[0].children);
  assert.equal(catalogMetadata(discovery, { category: "food" }).facets[0].key, "age");
  assert.equal(catalogMetadata(discovery, { category: "food", subcategory: "food-dry" }).facets[0].key, "protein");
  assert.deepEqual(catalogMetadata(discovery, { category: "supplies", subcategory: "food-dry" }).facets, []);
  assert.deepEqual(catalogMetadata(discovery, { category: "removed" }), { children: [], facets: [] });
  assert.deepEqual(catalogMetadata(null, { category: "food" }), { children: [], facets: [] });
});

test("Malformed URL numbers are safe and unknown metadata remains removable", () => {
  const filters = parseCatalogFilters(new URLSearchParams("page=NaN&size=-2&minPrice=Infinity&maxPrice=nope&sort=UNKNOWN&category=removed&facet=unknown:value"));
  assert.equal(filters.page, 0);
  assert.equal(filters.size, 12);
  assert.equal(filters.minPrice, undefined);
  assert.equal(filters.maxPrice, undefined);
  assert.equal(filters.sort, "RECOMMENDED");
  assert.equal(filters.category, "removed");
  assert.deepEqual(filters.facet, ["unknown:value"]);
});

test("All six server sort values survive URL parsing and serialization", () => {
  assert.deepEqual(PRODUCT_SORTS.map((item) => item.value), ["RECOMMENDED", "NEWEST", "PRICE_ASC", "PRICE_DESC", "RATING", "REVIEW_COUNT"]);
  for (const { value } of PRODUCT_SORTS) assert.equal(parseCatalogFilters(new URLSearchParams(catalogQuery({ sort: value }))).sort, value);
});

test("Back/forward URL parsing restores state rather than merging stale drafts", () => {
  const urls = ["category=food&facet=age:성묘&page=2", "category=supplies&sort=NEWEST", "category=food&facet=age:성묘&page=2"];
  const states = urls.map((url) => parseCatalogFilters(new URLSearchParams(url)));
  assert.deepEqual(states[0], states[2]);
  assert.deepEqual(states[1].facet, []);
  assert.equal(states[1].page, 0);
});

test("Superseded product requests cannot overwrite the latest result or surface late errors", async () => {
  const original = globalThis.fetch;
  const pending: { resolve: (value: Response) => void; reject: (reason: Error) => void }[] = [];
  globalThis.fetch = (() => new Promise<Response>((resolve, reject) => pending.push({ resolve, reject }))) as typeof fetch;
  const results: number[] = [];
  const errors: unknown[] = [];
  try {
    const cancelFirst = loadProductResults({ page: 0 }, (value) => results.push(value.page), (error) => errors.push(error));
    cancelFirst();
    const cancelSecond = loadProductResults({ page: 1 }, (value) => results.push(value.page), (error) => errors.push(error));
    pending[1].resolve(new Response(JSON.stringify({ page: 1 })));
    pending[0].reject(new Error("late failure"));
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(results, [1]); assert.equal(errors.length, 0);
    cancelSecond();
    const cancelThird = loadProductResults({}, () => results.push(3), (error) => errors.push(error));
    cancelThird(); pending[2].resolve(new Response(JSON.stringify({ page: 3 })));
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(results, [1]);
  } finally { globalThis.fetch = original; }
});

test("Zero option groups require explicit SKU choice, including a single SKU", () => {
  assert.equal(selectProductSku([], [], {}), null);
  assert.equal(selectProductSku([], [skus[0]], {}), null);
  assert.equal(selectProductSku([], skus, {}), null);
  assert.equal(selectProductSku([], skus, {}, 2), skus[1]);
  assert.equal(selectProductSku([], skus, {}, 999), null);
});

test("One option group requires a valid complete selection", () => {
  const options = [sku(1, [11]), sku(2, [12])];
  assert.equal(selectProductSku([groups[0]], options, {}), null);
  assert.equal(selectProductSku([groups[0]], options, { 10: 12 }), options[1]);
  assert.equal(selectProductSku([groups[0]], options, { 10: 999 }), null);
});

test("Two groups require exact selectedOptions independently of server option ordering", () => {
  assert.equal(selectProductSku(groups, skus, { 10: 11 }), null);
  assert.equal(selectProductSku(groups, skus, { 10: 11, 20: 21 }), skus[0]);
  assert.equal(selectProductSku(groups, skus, { 10: 11, 20: 22 }), null);
  assert.equal(selectProductSku(groups, skus, { 10: 11, 20: 21, 30: 31 }), null);
  const reversed = { ...skus[0], selectedOptions: [...skus[0].selectedOptions].reverse() };
  assert.equal(selectProductSku(groups, [reversed], { 10: 11, 20: 21 }), reversed);
});

test("Selected SKU is the authoritative source for price, stock, discount and subscription", () => {
  const selected = selectProductSku(groups, skus, { 10: 12, 20: 22 })!;
  assert.equal(selected, skus[1]);
  assert.equal(selected.price, 30123);
  assert.equal(selected.compareAtPrice, null);
  assert.equal(selected.discountRate, null);
  assert.equal(selected.availableQuantity, 3);
  assert.equal(selected.purchasable, false); // Positive stock must not invent eligibility.
  assert.equal(selected.subscribable, false);
});

test("Quantity validation follows the current SKU without resetting the user's input", () => {
  const limitedSku = { ...skus[0], availableQuantity: 3 };
  for (const value of ["", "0", "-1", "1.5", "NaN"]) assert.ok(productQuantityError(value, skus[0]));
  assert.equal(productQuantityError("7", skus[0]), null);
  assert.equal(productQuantityError("7", limitedSku), "현재 재고 3개 이하로 선택해 주세요.");
  assert.equal(productQuantityError("3", limitedSku), null);
});

test("Quantity errors do not replace option selection or sold-out guidance", () => {
  const soldOut = { ...skus[1], availableQuantity: 0 };
  for (const selected of [null, soldOut, skus[1]]) {
    for (const value of ["1", "0", "1.5", "7"]) assert.equal(productQuantityError(value, selected), null);
  }
});

test("Review collection copy leaves the empty product state unchanged", () => {
  assert.equal(reviewCollectionCopy([]), null);
});

test("Review collection copy is neutral when every product has zero reviews", () => {
  assert.deepEqual(reviewCollectionCopy([{ reviewCount: 0 }, { reviewCount: 0 }]), {
    title: "첫 리뷰를 기다리는 상품", description: "아직 리뷰가 쌓이지 않은 상품을 먼저 만나보세요.",
  });
});

test("Review collection copy reflects some or all products with server reviews", () => {
  for (const counts of [[0, 1], [1, 9]]) assert.deepEqual(reviewCollectionCopy(counts.map((reviewCount) => ({ reviewCount }))), {
    title: "많이 이야기하는 상품", description: "다른 반려가족의 리뷰가 쌓인 상품을 만나보세요.",
  });
});

for (const { label, filters, valid } of [
  { label: "min < max", filters: { minPrice: 10, maxPrice: 20 }, valid: true },
  { label: "min == max", filters: { minPrice: 10, maxPrice: 10 }, valid: true },
  { label: "min > max", filters: { minPrice: 20, maxPrice: 10 }, valid: false },
  { label: "min only", filters: { minPrice: 10 }, valid: true },
  { label: "max only", filters: { maxPrice: 10 }, valid: true },
  { label: "zero range", filters: { minPrice: 0, maxPrice: 0 }, valid: true },
  { label: "zero max", filters: { minPrice: 1, maxPrice: 0 }, valid: false },
]) test(`Price range: ${label}`, () => {
  assert.equal(catalogPriceRangeError(filters) === null, valid);
});

test("Invalid URL price range preserves input and never calls Product API", async () => {
  const original = globalThis.fetch;
  let requests = 0;
  globalThis.fetch = (async () => { requests += 1; return new Response("{}"); }) as typeof fetch;
  try {
    const filters = parseCatalogFilters(new URLSearchParams("minPrice=200&maxPrice=100&q=food"));
    const errors: unknown[] = [];
    loadProductResults(filters, () => assert.fail("Invalid range must not load products"), (error) => errors.push(error));
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(requests, 0);
    assert.equal((errors[0] as Error).message, catalogPriceRangeError(filters));
    assert.equal(filters.minPrice, 200);
    assert.equal(filters.maxPrice, 100);
    assert.equal(filters.q, "food");
  } finally { globalThis.fetch = original; }
});

test("Slow wishlist lookup survives a Cart mutation and blocks wishlist mutation until ready", async () => {
  const original = globalThis.fetch;
  let finishWishlist!: (response: Response) => void;
  const wishlistResponse = new Promise<Response>((resolve) => { finishWishlist = resolve; });
  const paths: string[] = [];
  globalThis.fetch = (async (input) => {
    paths.push(String(input));
    return String(input) === "/api/wishlist" ? wishlistResponse : new Response(null, { status: 204 });
  }) as typeof fetch;
  const generation = { current: 0 };
  const published: ProductWishlistState[] = [];
  try {
    loadProductWishlist(generation, 7, "4", async () => (await commerceFinalApi.wishlist()).items.some((item) => item.productId === 4), (state) => published.push(state), () => assert.fail("Lookup must succeed"));
    assert.notEqual(currentProductWishlist(published.at(-1)!, 7, "4").status, "ready");
    await commerceFinalApi.addCart(1, 1, "test-csrf");
    finishWishlist(new Response(JSON.stringify({ items: [{ productId: 4 }] })));
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(paths, ["/api/wishlist", "/api/cart/items"]);
    assert.deepEqual(currentProductWishlist(published.at(-1)!, 7, "4"), { memberId: 7, productId: "4", status: "ready", value: true });
  } finally { globalThis.fetch = original; }
});

test("Previous wishlist generations and cleanup cannot overwrite the current identity", async () => {
  const generation = { current: 0 };
  const published: ProductWishlistState[] = [];
  const pending: { resolve: (value: boolean) => void; reject: (error: Error) => void }[] = [];
  const load = () => new Promise<boolean>((resolve, reject) => pending.push({ resolve, reject }));
  const publish = (state: ProductWishlistState) => published.push(state);
  const cancelOld = loadProductWishlist(generation, 7, "4", load, publish, () => assert.fail("Stale errors must be ignored"));
  const cancelCurrent = loadProductWishlist(generation, 8, "5", load, publish, () => assert.fail("Current lookup succeeds"));
  cancelOld(); // A previous cleanup must not invalidate a newer read.
  pending[1].resolve(true);
  await new Promise((resolve) => setImmediate(resolve));
  pending[0].resolve(false);
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(published.at(-1), { memberId: 8, productId: "5", status: "ready", value: true });
  cancelCurrent();
  const cancelNext = loadProductWishlist(generation, 8, "6", load, publish, () => assert.fail("Cancelled auth/product lookup must not report errors"));
  cancelNext(); pending[2].reject(new Error("late failure"));
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(published.at(-1)!.status, "loading");
});

test("Wishlist values require the current authenticated member/product and ready state", () => {
  const ready: ProductWishlistState = { memberId: 7, productId: "4", status: "ready", value: true };
  for (const [memberId, productId] of [[null, "4"], [8, "4"], [7, "5"]] as const) {
    const current = currentProductWishlist(ready, memberId, productId);
    assert.equal(current.value, false);
    assert.notEqual(current.status, "ready");
  }
  for (const status of ["loading", "error"] as const) {
    const current = currentProductWishlist({ ...ready, status }, 7, "4");
    assert.equal(current.value, false);
    assert.notEqual(current.status, "ready");
  }
  assert.equal(currentProductWishlist(ready, 7, "4").value, true);
});

test("Wishlist read failure clears the value and exposes a local error state", async () => {
  const published: ProductWishlistState[] = [];
  const errors: unknown[] = [];
  const failure = new Error("lookup failed");
  loadProductWishlist({ current: 0 }, 7, "4", () => Promise.reject(failure), (state) => published.push(state), (error) => errors.push(error));
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(published.at(-1), { memberId: 7, productId: "4", status: "error", value: false });
  assert.deepEqual(errors, [failure]);
});
