import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
const source = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

test("customer image correction is final, scoped, contained, and fixes direct gallery grid item", () => {
  const layout = source("../app/layout.tsx");
  const css = source("../app/ui-ux.css");
  assert.ok(layout.indexOf('import "./ui-ux.css"') > layout.indexOf('import "./visual-closure-v3.css"'));
  assert.match(css, /@scope \(\.customer-theme\)/);
  assert.doesNotMatch(css, /\.admin-theme/);
  assert.match(css, /\.catalog-product-card \.product-card-media > \.product-thumbnail\s*\{[^}]*position:absolute[^}]*height:100%[^}]*object-fit:contain/);
  assert.match(css, /\.product-detail-layout > \.catalog-gallery\s*\{[^}]*grid-area:gallery/);
  assert.match(css, /safe-area-inset-bottom/);
  assert.match(css, /max-height:479px/);
});

test("filter application uses client navigation and preserves catalog return query", () => {
  const page = source("../app/products/page.tsx");
  const header = source("./app-header.tsx");
  assert.match(page, /rememberCatalogReturn\(catalogHref\(filters\)\)/);
  assert.doesNotMatch(page, /window\.location\.assign/);
  assert.match(header, /buildLoginHref\(currentHref\)/);
  assert.match(header, /searchParams\.toString\(\)/);
});

test("missing purchase policies remain explicit rather than fabricated guarantees", () => {
  const policies = source("./trust-pages.tsx");
  const footer = source("./app-footer.tsx");
  assert.match(policies, /배송비·무료배송 조건/);
  assert.match(policies, /반품 배송비·부담 기준/);
  assert.match(policies, /정보 미제공/);
  assert.match(policies, /테스트 결제는 실제 결제가 아닙니다/);
  assert.match(footer, /판매자 정보·이용약관·개인정보처리방침은 아직 제공되지 않습니다/);
  assert.doesNotMatch(policies, /익일 도착 보장|무조건 무료 반품/);
});

test("comparison keeps authoritative facts and offers a difference-only view without emphasizing AI failure", () => {
  const comparison = source("./comparison-screen.tsx");
  assert.match(comparison, /다른 항목만 보기/);
  assert.match(comparison, /aria-pressed=\{differencesOnly\}/);
  assert.match(comparison, /formatComparisonFacets\(fact.facets\)/);
  assert.match(comparison, /data\.aiStatus === "AVAILABLE" && data\.aiSummary/);
  assert.doesNotMatch(comparison, /비교 요약을 준비하지 못했어요/);
});

test("trust refresh validates against the newest purchase selection and ignores stale route responses", () => {
  const detail = source("./product-detail-screen.tsx");
  assert.match(detail, /latestPurchaseDraftRef\.current = \{ productId, selection, skuId: cartSkuId, quantity \}/);
  assert.match(detail, /request !== trustRefreshRequest\.current \|\| currentProductIdRef\.current !== productId/);
  assert.match(detail, /validateProductDraft\(latest, latestPurchaseDraftRef\.current\)/);
  assert.match(detail, /function changePurchaseQuantity\(nextQuantity: string\)/);
  assert.match(detail, /onQuantityChange=\{changePurchaseQuantity\}/);
});
