import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./product-detail-screen.tsx", import.meta.url), "utf8");
const trustSource = readFileSync(new URL("./product-trust-sections.tsx", import.meta.url), "utf8");
const purchaseSource = readFileSync(new URL("./product-purchase-panel.tsx", import.meta.url), "utf8");

test("Sold-out SKU blocks Cart even without a quantity error and has text guidance", () => {
  assert.match(source, /if \(!product \|\| !selectedSku\?\.purchasable \|\| busy\)/);
  assert.match(purchaseSource, /disabled=\{busy \|\| !selectedSku\?\.purchasable \|\| Boolean\(quantityError\)\}/);
  assert.match(purchaseSource, /현재 품절 · 구매 불가/);
});

test("canonical product detail does not invoke the legacy subscription endpoint", () => {
  assert.doesNotMatch(source, /subscriptionApi\.create/);
  assert.doesNotMatch(source, /\/api\/subscriptions/);
});

test("canonical product detail starts V2 subscription selection at subscriptions/new", () => {
  assert.match(source, /CANONICAL_SUBSCRIPTION_START_HREF = "\/subscriptions\/new"/);
  assert.match(source, /commerceFinalApi\.addCart/);
  assert.match(source, /commerceFinalApi\.addWishlist/);
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
