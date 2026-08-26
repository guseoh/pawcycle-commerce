import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./product-detail-screen.tsx", import.meta.url), "utf8");
const trustSource = readFileSync(new URL("./product-trust-sections.tsx", import.meta.url), "utf8");

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
  assert.match(trustSource, /Promise\.all\(\[loadReviews\(reviewPage\), loadMyReview\(\), onTrustRefresh\(\)\]\)/);
  assert.doesNotMatch(source, /dangerouslySetInnerHTML/);
  assert.doesNotMatch(trustSource, /dangerouslySetInnerHTML/);
  assert.doesNotMatch(trustSource, /memberId|email/);
});
