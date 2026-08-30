import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { isProductOptionValueAvailable, selectProductSku } from "../lib/product-selection.ts";

const source = readFileSync(new URL("./product-detail-screen.tsx", import.meta.url), "utf8");
const trustSource = readFileSync(new URL("./product-trust-sections.tsx", import.meta.url), "utf8");
const purchaseSource = readFileSync(new URL("./product-purchase-panel.tsx", import.meta.url), "utf8");

test("Sold-out SKU blocks Cart even without a quantity error and has text guidance", () => {
  assert.match(source, /if \(!product \|\| !selectedSku\?\.purchasable \|\| busy\)/);
  assert.match(purchaseSource, /disabled=\{busy \|\| !selectedSku\?\.purchasable \|\| Boolean\(quantityError\)\}/);
  assert.match(purchaseSource, /현재 품절 · 구매 불가/);
});

test("option controls expose only server-backed combinations and auto-select one purchasable SKU", () => {
  const groups = [
    { optionGroupId: 1, name: "색상", displayOrder: 1, values: [{ optionValueId: 11, value: "초록", displayOrder: 1 }, { optionValueId: 12, value: "노랑", displayOrder: 2 }] },
    { optionGroupId: 2, name: "용량", displayOrder: 2, values: [{ optionValueId: 21, value: "소", displayOrder: 1 }, { optionValueId: 22, value: "대", displayOrder: 2 }] },
  ];
  const sku = (skuId: number, options: [number, number][], purchasable = true) => ({ skuId, skuName: "옵션", price: 1000, compareAtPrice: null, discountRate: null, selectedOptions: options.map(([optionGroupId, optionValueId]) => ({ optionGroupId, optionValueId, groupName: "", value: "" })), subscribable: false, availableDeliveryCycles: [], availableQuantity: purchasable ? 5 : 0, purchasable });
  const skus = [sku(101, [[1, 11], [2, 21]]), sku(102, [[1, 12], [2, 22]])];
  assert.equal(isProductOptionValueAvailable(groups, skus, { 1: 11 }, 2, 22), false);
  assert.equal(isProductOptionValueAvailable(groups, skus, {}, 1, 12), true);
  assert.equal(selectProductSku([], [sku(201, [[0, 0]])], {}, null)?.skuId, 201);
  assert.equal(selectProductSku([], [sku(202, [[0, 0]], false)], {}, null), null);
});

test("canonical product detail does not invoke the legacy subscription endpoint", () => {
  assert.doesNotMatch(source, /subscriptionApi\.create/);
  assert.doesNotMatch(source, /\/api\/subscriptions/);
});

test("canonical product detail keeps subscription entry in order detail", () => {
  const orderSource = readFileSync(new URL("./commerce-order-detail.tsx", import.meta.url), "utf8");
  assert.doesNotMatch(source, /CANONICAL_SUBSCRIPTION_START_HREF/);
  assert.match(source, /commerceFinalApi\.addCart/);
  assert.match(source, /commerceFinalApi\.addWishlist/);
  assert.doesNotMatch(source, /정기배송 시작|새 정기배송|\/subscriptions\/new/);
  assert.match(orderSource, /정기배송으로 다시 받기/);
  assert.match(orderSource, /OrderSubscriptionOptionsPanel/);
});

test("Product Detail consumes additive trust content without HTML interpretation", () => {
  assert.match(source, /shortDescription/);
  assert.match(source, /detailSections/);
  assert.match(source, /product-detail-sections/);
  assert.match(trustSource, /averageRating === null/);
  assert.match(trustSource, /아직 리뷰 없음/);
  assert.match(trustSource, /productApi\.reviews/);
  assert.match(trustSource, /productApi\.myReview/);
  assert.match(trustSource, /productApi\.createReview/);
  assert.match(trustSource, /productApi\.updateReview/);
  assert.match(trustSource, /productApi\.deleteReview/);
  assert.match(trustSource, /productApi\.questions/);
  assert.match(trustSource, /productApi\.createQuestion/);
  assert.doesNotMatch(source, /dangerouslySetInnerHTML/);
  assert.doesNotMatch(trustSource, /dangerouslySetInnerHTML/);
  assert.doesNotMatch(trustSource, /memberId|email/);
});

test("Product Trust ignores stale requests and separates mutation success from refresh failure", () => {
  assert.match(trustSource, /reviewRequestGeneration/);
  assert.match(trustSource, /questionRequestGeneration/);
  assert.match(trustSource, /myReviewRequestGeneration/);
  assert.match(trustSource, /generation !== reviewRequestGeneration\.current/);
  assert.match(trustSource, /generation !== questionRequestGeneration\.current/);
  assert.match(trustSource, /generation !== myReviewRequestGeneration\.current/);
  assert.match(trustSource, /리뷰 저장은 완료됐지만 최신 정보를 모두 불러오지 못했습니다/);
  assert.match(trustSource, /상품 문의 등록은 완료됐지만 최신 정보를 모두 불러오지 못했습니다/);
  assert.match(trustSource, /error\.code === "AUTH_REQUIRED"\) auth\.markAnonymous\(\)/);
});
