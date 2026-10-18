import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./product-detail-screen.tsx", import.meta.url), "utf8");
const panel = readFileSync(new URL("./product-purchase-panel.tsx", import.meta.url), "utf8");

test("audit18 detail preserves explicit cart action and CSRF without authentication replay", () => {
  assert.match(source, /auth\.executeWithCsrf\(\(csrf\) => cartApi\.add/);
  assert.match(source, /onCart=\{\(\) => void addCart\(\)\}/);
  assert.doesNotMatch(source, /subscriptionApi|subscriptions\/new|pendingMutation|replayMutation/);
  assert.match(panel, /주문 상세에서 ‘정기배송으로 다시 받기’/);
});

test("audit18 price, jump navigation, filtered recommendations and sheet keyboard recovery", () => {
  assert.match(source, /productPriceRange\(product\?\.skus/);
  assert.match(source, /href="#product-reviews-title"/);
  assert.match(source, /href="#product-questions-title"/);
  assert.match(source, /!sheetOpen && \(!purchaseVisible \|\| !selectedSku\)/);
  assert.match(source, /restoreFocus=\{\(\) => \(purchaseTriggerRef\.current \?\? purchaseSummaryRef\.current\)\?\.focus\(\)\}/);
  assert.match(panel, /onCancel=\{\(event\) => \{ event\.preventDefault\(\); onClose\(\); \}\}/);
  assert.match(source, /item\.category\?\.categoryId === product!\.category\.categoryId/);
  assert.match(source, /item\.strategy === "COMPLEMENTARY"/);
  assert.match(source, /href=\{catalogReturn\}/);
  assert.match(source, /성분·소재/);
  assert.match(source, /실측·규격/);
  assert.doesNotMatch(source, /dangerouslySetInnerHTML/);
});
