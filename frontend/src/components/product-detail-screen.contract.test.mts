import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./product-detail-screen.tsx", import.meta.url), "utf8");

test("canonical product detail does not invoke the legacy subscription endpoint", () => {
  assert.doesNotMatch(source, /subscriptionApi\.create/);
  assert.doesNotMatch(source, /\/api\/subscriptions/);
});

test("canonical product detail starts V2 subscription selection at subscriptions/new", () => {
  assert.match(source, /CANONICAL_SUBSCRIPTION_START_HREF = "\/subscriptions\/new"/);
  assert.match(source, /commerceFinalApi\.addCart/);
  assert.match(source, /commerceFinalApi\.addWishlist/);
});
