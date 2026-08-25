import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { cartQuantityError, cartQuantityForUpdate } from "../lib/frontend-utils.ts";

const cartSource = readFileSync(new URL("../app/cart/page.tsx", import.meta.url), "utf8");
const subscriptionSource = readFileSync(new URL("./mvp2-subscription-detail.tsx", import.meta.url), "utf8");
const addressesSource = readFileSync(new URL("../app/addresses/page.tsx", import.meta.url), "utf8");
const wishlistSource = readFileSync(new URL("../app/wishlist/page.tsx", import.meta.url), "utf8");
const billingSource = readFileSync(new URL("../app/billing-methods/page.tsx", import.meta.url), "utf8");
const notificationSource = readFileSync(new URL("./notification-screen.tsx", import.meta.url), "utf8");
const subscriptionStartSource = readFileSync(new URL("./mvp2-subscription-start.tsx", import.meta.url), "utf8");
const legacySubscriptionDetailSource = readFileSync(new URL("./subscription-detail-screen.tsx", import.meta.url), "utf8");
const homeSource = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
const productsSource = readFileSync(new URL("../app/products/page.tsx", import.meta.url), "utf8");
const headerSource = readFileSync(new URL("./app-header.tsx", import.meta.url), "utf8");
const globalStylesSource = readFileSync(new URL("../app/globals.css", import.meta.url), "utf8");

test("장바구니 수량 입력은 최종 draft만 적용한다", () => {
  const typedDrafts = ["1", "12"];
  const pastedDrafts = ["12"];

  assert.deepEqual(typedDrafts.map(cartQuantityError), [null, null]);
  assert.deepEqual(pastedDrafts.map(cartQuantityError), [null]);
  assert.deepEqual([cartQuantityForUpdate(typedDrafts.at(-1)!)], [12]);
  assert.deepEqual([cartQuantityForUpdate(pastedDrafts.at(-1)!)], [12]);
  assert.match(cartSource, /onChange=\{\(event\) => updateDraft\(item\.skuId, event\.target\.value, item\.availableQuantity\)\}/);
  assert.match(cartSource, /onClick=\{\(\) => void applyQuantity\(item\)\}/);
  assert.equal((cartSource.match(/commerceFinalApi\.updateCart/g) ?? []).length, 1);
  assert.doesNotMatch(cartSource, /item\.skuCode/);
});

test("구독 플랜 조회는 오류와 정상 empty 상태를 분리한다", () => {
  assert.match(subscriptionSource, /const \[plansError, setPlansError\]/);
  assert.doesNotMatch(subscriptionSource, /setPlans\(\[\]\)/);
  assert.match(subscriptionSource, /plansError \? <ErrorState/);
  assert.match(subscriptionSource, /plansReady && allowedCycles\.length === 0/);
});

test("회원별 계정 화면은 새 인스턴스와 stale 응답 guard를 사용한다", () => {
  for (const source of [addressesSource, wishlistSource, billingSource]) {
    assert.match(source, /key=\{auth\.memberId\}/);
    assert.match(source, /activeRef\.current = false/);
    assert.match(source, /request !== requestRef\.current/);
  }
  assert.match(wishlistSource, /reason\.code === "AUTH_REQUIRED"\) \{\s*markAnonymous\(\)/);
});

test("알림 재조회 실패는 기존 목록을 무효화한다", () => {
  assert.match(notificationSource, /async function refresh\(\) \{\s*setItems\(null\)/);
  assert.match(notificationSource, /if \(!items\) return <ErrorState/);
});

test("header와 My의 계정 action 경계를 유지한다", () => {
  const mySource = readFileSync(new URL("../app/my/page.tsx", import.meta.url), "utf8");
  assert.doesNotMatch(headerSource, /로그아웃/);
  assert.match(headerSource, /const \{ status, memberId \} = useAuth\(\)/);
  assert.match(headerSource, /request !== requestRef\.current/);
  assert.match(headerSource, /setCartCount\(0\);\s*setWishlistCount\(0\)/);
  assert.match(mySource, /async function loadAllSubscriptions/);
  assert.match(mySource, /subscriptions\.length < first\.body\.totalElements/);
  assert.match(mySource, /sort\(\(left, right\) => left\.nextScheduledDate!/);
  assert.match(mySource, /LogoutControl/);
});

test("카테고리 탐색은 공개 API authority와 인증 복구 경로를 사용한다", () => {
  assert.match(productsSource, /categoryApi\.list\(\)/);
  assert.match(productsSource, /categoryState\.categories\.map/);
  assert.doesNotMatch(productsSource, /value="food"|value="treats"|value="hygiene"|value="toilet"/);
  assert.match(homeSource, /categoryApi\.list\(\)/);
  assert.match(homeSource, /products\?petType=DOG/);
  assert.match(homeSource, /products\?petType=CAT/);
  assert.match(homeSource, /products\?category=\$\{encodeURIComponent\(category\.slug\)\}/);
  assert.match(homeSource, /productApi\.list\(\{ page: 0, size: 4, sort: "NEWEST" \}\)/);
  assert.match(homeSource, /buildLoginHref\("\/"\)/);
  assert.match(headerSource, /<details className="category-navigation"/);
  assert.match(headerSource, />카테고리<\/summary>/);
  assert.match(headerSource, /categoryApi\.list\(\)/);
  assert.match(headerSource, /status === "anonymous" \|\| status === "error"/);
  assert.match(headerSource, /buildLoginHref\(pathname\)/);
  assert.match(globalStylesSource, /\.category-navigation summary/);
  assert.match(globalStylesSource, /\.category-navigation-menu \{ position: static/);
});

test("주문 재담기와 요청 dialog는 부분 성공과 키보드 경계를 보호한다", () => {
  const orderSource = readFileSync(new URL("./commerce-order-detail.tsx", import.meta.url), "utf8");
  assert.match(orderSource, /reorderedSkuIds\.current\.has\(item\.skuId\)/);
  assert.match(orderSource, /if \(addedThisAttempt > 0\) notifyCommerceChanged\(\)/);
  assert.match(orderSource, /다시 시도하면 성공한 상품은 중복으로 담지 않습니다/);
  assert.match(orderSource, /event\.key === "Escape"/);
  assert.match(orderSource, /event\.key !== "Tab"/);
  assert.match(orderSource, /requestOpener\.current\?\.focus\(\)/);
});

test("관련 상품은 상세 조회와 독립된 loading·retry 상태를 사용한다", () => {
  const productSource = readFileSync(new URL("./product-detail-screen.tsx", import.meta.url), "utf8");
  assert.match(productSource, /const \[relatedRetry, setRelatedRetry\]/);
  assert.match(productSource, /const \[relatedLoading, setRelatedLoading\]/);
  assert.match(productSource, /onRetry=\{\(\) => setRelatedRetry/);
  assert.match(productSource, /relatedLoading \? <section/);
  assert.match(productSource, /같은 카테고리의 다른 상품이 아직 없습니다/);
});

test("Root layout은 공통 Footer를 연결한다", () => {
  const layoutSource = readFileSync(new URL("../app/layout.tsx", import.meta.url), "utf8");
  const footerSource = readFileSync(new URL("./app-footer.tsx", import.meta.url), "utf8");
  assert.match(layoutSource, /AppFooter/);
  assert.match(footerSource, /\/shipping/);
  assert.match(footerSource, /\/returns/);
  assert.match(footerSource, /\/faq/);
  assert.match(footerSource, /\/notice/);
  assert.match(footerSource, /\/support/);
  assert.match(footerSource, /쇼핑/);
  assert.match(footerSource, /주문 \/ 계정/);
  assert.match(footerSource, /고객지원/);
});

test("홈은 Commerce 진입 흐름과 인증별 상태를 유지한다", () => {
  assert.match(homeSource, /aria-labelledby="home-title"/);
  assert.match(homeSource, /href="\/products"/);
  assert.match(homeSource, /href="\/subscriptions"/);
  assert.match(homeSource, /function CompactDiscovery/);
  assert.match(homeSource, /function HomeProductPreview/);
  assert.match(homeSource, /function SubscriptionValue/);
  assert.doesNotMatch(homeSource, /function QuickActions/);
  assert.match(homeSource, /recommendationApi\.products/);
  assert.match(homeSource, /v2Api\.pets\.list/);
  assert.match(homeSource, /auth\.status === "loading"/);
  assert.match(homeSource, /auth\.status === "anonymous"/);
  assert.match(homeSource, /auth\.status === "authenticated"/);
  assert.match(homeSource, /auth\.status === "error"/);
  assert.match(homeSource, /recommendationLoading/);
  assert.match(homeSource, /aria-describedby="recommendation-help"/);
  assert.match(homeSource, /alt=\{`\$\{item\.name\} 상품 이미지`\}/);
  assert.match(globalStylesSource, /@media \(max-width: 767px\)/);
  assert.match(globalStylesSource, /@media \(max-width: 380px\)/);
  assert.match(globalStylesSource, /:focus-visible/);
  assert.match(globalStylesSource, /aspect-ratio: 4 \/ 3/);
  assert.match(globalStylesSource, /@media \(max-width: 480px\)/);
});

test("카탈로그 카드는 실제 상품 링크와 일관된 이미지·페이지 상태를 제공한다", () => {
  assert.match(productsSource, /className="product-card-media"/);
  assert.match(productsSource, /aria-label=\{`\$\{product\.name\} 상품 상세 보기`\}/);
  assert.match(productsSource, /className=\{`product-availability/);
  assert.match(productsSource, /className="pagination-row"/);
  assert.doesNotMatch(productsSource, /userFacingCatalogLabel/);
  assert.match(globalStylesSource, /\.product-card-media \{ display: grid; aspect-ratio: 4 \/ 3/);
  assert.match(globalStylesSource, /\.pagination-row \{ display: flex/);
});

test("결제 성공·실패 callback은 Toss v2 위젯과 backend confirm 경계를 유지한다", () => {
  const checkoutSource = readFileSync(new URL("../app/checkout/page.tsx", import.meta.url), "utf8");
  const widgetSource = readFileSync(new URL("./toss-payment-widget.tsx", import.meta.url), "utf8");
  const successSource = readFileSync(new URL("../app/checkout/success/page.tsx", import.meta.url), "utf8");
  const failSource = readFileSync(new URL("../app/checkout/fail/page.tsx", import.meta.url), "utf8");
  assert.match(checkoutSource, /TossPaymentWidget/);
  assert.match(widgetSource, /setAmount\(\{ currency: "KRW", value: checkout\.amount \}\)/);
  assert.match(widgetSource, /renderPaymentMethods/);
  assert.match(widgetSource, /renderAgreement/);
  assert.match(widgetSource, /requestPayment/);
  assert.match(widgetSource, /NEXT_PUBLIC_TOSS_TEST_CLIENT_KEY/);
  assert.match(successSource, /commerceFinalApi\.confirmToss/);
  assert.match(successSource, /expected\.amount/);
  assert.doesNotMatch(failSource, /confirmToss/);
});

test("사용자 화면은 내부 식별자를 노출하지 않는다", () => {
  const successSource = readFileSync(new URL("../app/checkout/success/page.tsx", import.meta.url), "utf8");
  assert.doesNotMatch(subscriptionStartSource, /상품 #\$\{productContext\}|옵션 #/);
  assert.doesNotMatch(legacySubscriptionDetailSource, /Subscription #|\( #/);
  assert.doesNotMatch(successSource, /결제 확인 번호/);
});
