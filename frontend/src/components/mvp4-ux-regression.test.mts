import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { cartQuantityError, cartQuantityForUpdate } from "../lib/frontend-utils.ts";

const cartSource = readFileSync(new URL("../app/cart/page.tsx", import.meta.url), "utf8");
const subscriptionSource = readFileSync(new URL("./subscription-detail.tsx", import.meta.url), "utf8");
const addressesSource = readFileSync(new URL("../app/addresses/page.tsx", import.meta.url), "utf8");
const wishlistSource = readFileSync(new URL("../app/wishlist/page.tsx", import.meta.url), "utf8");
const billingSource = readFileSync(new URL("../app/billing-methods/page.tsx", import.meta.url), "utf8");
const checkoutSource = readFileSync(new URL("../app/checkout/page.tsx", import.meta.url), "utf8");
const tossPaymentWidgetSource = readFileSync(new URL("./toss-payment-widget.tsx", import.meta.url), "utf8");
const notificationSource = readFileSync(new URL("./notification-screen.tsx", import.meta.url), "utf8");
const subscriptionStartSource = readFileSync(new URL("./subscription-start.tsx", import.meta.url), "utf8");
const homeSource = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
const productsSource = readFileSync(new URL("../app/products/page.tsx", import.meta.url), "utf8");
const productCardSource = readFileSync(new URL("./catalog-product-card.tsx", import.meta.url), "utf8");
const discoverySource = readFileSync(new URL("./catalog-discovery.ts", import.meta.url), "utf8");
const headerSource = readFileSync(new URL("./app-header.tsx", import.meta.url), "utf8");
const globalStylesSource = readFileSync(new URL("../app/globals.css", import.meta.url), "utf8");
const shoppingStylesSource = readFileSync(new URL("../app/shopping.css", import.meta.url), "utf8");
const productDetailSource = readFileSync(new URL("./product-detail-screen.tsx", import.meta.url), "utf8");
const purchasePanelSource = readFileSync(new URL("./product-purchase-panel.tsx", import.meta.url), "utf8");
const recommendationSource = readFileSync(new URL("./recommendation-card.tsx", import.meta.url), "utf8");
const mySource = readFileSync(new URL("../app/my/page.tsx", import.meta.url), "utf8");
const orderListSource = readFileSync(new URL("./commerce-order-list.tsx", import.meta.url), "utf8");
const subscriptionListSource = readFileSync(new URL("./subscription-list.tsx", import.meta.url), "utf8");
const layoutSource = readFileSync(new URL("../app/layout.tsx", import.meta.url), "utf8") + readFileSync(new URL("./customer-shell.tsx", import.meta.url), "utf8");

test("MVP4 review correction은 실패한 작업과 안전한 복귀를 보존한다", () => {
  assert.match(cartSource, /type CartMutationError = \{ operation: "update" \| "delete"/);
  assert.match(cartSource, /itemErrors\[item\.skuId\]\.operation === "delete" \? remove\(item\) : applyQuantity\(item\)/);
  assert.match(addressesSource, /useSearchParams/);
  assert.match(addressesSource, /sanitizeReturnTo/);
  assert.match(addressesSource, /candidateReturnTo === "\/checkout"/);
  assert.match(addressesSource, /router\.push\(returnTo\)/);
  assert.match(productsSource, /value !== undefined && value !== ""/);
  assert.doesNotMatch(productsSource, /value !== false/);
});

test("헤더·위시리스트·상품 카드 상태는 URL/viewport/member 경계를 유지한다", () => {
  assert.match(headerSource, /useSearchParams/);
  assert.match(headerSource, /setSearchDraft\(pathname === "\/products" \? searchParams\.get\("q"\)/);
  assert.match(headerSource, /setActiveCategory\(searchParams\.get\("category"\)/);
  assert.doesNotMatch(headerSource, /window\.scrollY/);
  assert.doesNotMatch(headerSource, /const nextCompact = [^\n]*window\.innerWidth <= 1023/);
  assert.match(headerSource, /window\.innerWidth <= 1023/);
  assert.match(headerSource, /categoryExpanded/);
  assert.match(headerSource, /header-actions/);
  assert.doesNotMatch(shoppingStylesSource, /nth-last-child/);
  assert.match(wishlistSource, /undoTimerRef/);
  assert.match(wishlistSource, /if \(removed\) \{ setRemoveBlockedMessage/);
  assert.match(productCardSource, /new Map<number, WishlistCacheEntry>/);
  assert.match(productCardSource, /pawcycle-commerce-changed/);
  assert.match(productCardSource, /auth\.markAnonymous\(\)/);
});

test("구독 변경·배송지·새 구독은 서버 허용 범위와 명시적 선택을 따른다", () => {
  assert.match(subscriptionSource, /hasAction\("UPDATE_SHIPPING_ADDRESS"\)/);
  assert.match(subscriptionSource, /SavedAddressState/);
  assert.match(subscriptionSource, /PAYMENT_SUPPORT_REQUIRED/);
  assert.match(subscriptionSource, /STOCK_UNAVAILABLE/);
  assert.match(subscriptionSource, /이 플랜으로 변경/);
  assert.match(subscriptionStartSource, /preferredCycle !== null .* : null/);
  assert.match(subscriptionStartSource, /startQuery\.fromOrderId !== null/);
});

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
  assert.match(headerSource, /const auth = useAuth\(\)/);
  assert.match(headerSource, /const \{ status, memberId \} = auth/);
  assert.match(headerSource, /request !== requestRef\.current/);
  assert.match(headerSource, /setCartCount\(null\);\s*setWishlistCount\(0\)/);
  assert.match(mySource, /async function loadAllSubscriptions/);
  assert.match(mySource, /subscriptions\.length < first\.body\.totalElements/);
  assert.match(mySource, /sort\(\(left, right\) => left\.nextScheduledDate!/);
  assert.match(mySource, /LogoutControl/);
});

test("모바일 메뉴는 닫힌 category의 링크를 focus trap 경계에 넣지 않는다", () => {
  assert.match(headerSource, /function drawerFocusable/);
  assert.match(headerSource, /node\.closest\("details:not\(\[open\]\)"\)/);
  assert.match(headerSource, /!closedDetails \|\| node\.tagName === "SUMMARY"/);
  assert.match(headerSource, /document\.removeEventListener\("keydown", onKeyDown\)/);
  assert.match(headerSource, /restoreMenuFocus\.current/);
  assert.match(headerSource, /requestAnimationFrame\(\(\) => trigger\.focus\(\)\)/);
  assert.match(headerSource, /if \(event\.key === "Escape"\) \{[\s\S]*?if \(menuOpen\) restoreMenuFocus\.current = true;/);
});

test("카테고리 탐색은 공개 API authority와 인증 복구 경로를 사용한다", () => {
  assert.match(productsSource, /useCatalogDiscovery/);
  assert.match(productsSource, /metadata\?\.categories\.map/);
  assert.doesNotMatch(productsSource, /value="food"|value="treats"|value="hygiene"|value="toilet"/);
  assert.match(homeSource, /useCatalogDiscovery/);
  assert.match(homeSource, /products\?petType=DOG/);
  assert.match(homeSource, /products\?petType=CAT/);
  assert.match(homeSource, /products\?category=\$\{encodeURIComponent\(category\.slug\)\}/);
  assert.match(homeSource, /title="지금 많이 찾는 상품"/);
  assert.doesNotMatch(homeSource, /Trending|트렌딩|추천 상품 미리보기/);
  assert.match(homeSource, /buildLoginHref\("\/"\)/);
  assert.match(headerSource, /className="header-catalog"/);
  assert.match(headerSource, /className="category-navigation-overlay"/);
  assert.match(headerSource, /useCatalogDiscovery/);
  assert.match(discoverySource, /catalogDiscoveryApi\.get\(\)/);
  assert.match(headerSource, /status !== "authenticated"/);
  assert.match(headerSource, /buildLoginHref\(pathname\)/);
  assert.match(shoppingStylesSource, /\.category-navigation-overlay/);
});

test("서버 재주문과 요청 dialog는 부분 성공과 키보드 경계를 보호한다", () => {
  const orderSource = readFileSync(new URL("./commerce-order-detail.tsx", import.meta.url), "utf8");
  assert.match(orderSource, /commerceFinalApi\.quickReorder\(orderId, csrf, attempt\.key\)/);
  assert.doesNotMatch(orderSource, /commerceFinalApi\.addCart/);
  assert.doesNotMatch(orderSource, /for \(const item of order\.items\)/);
  assert.match(orderSource, /quickReorderAttempt\.current/);
  assert.match(orderSource, /response\.addedItems\.length > 0/);
  assert.match(orderSource, /response\.skippedItems\.length/);
  assert.match(orderSource, /newIdempotencyKey\(\)/);
  assert.match(orderSource, /CommerceOverlay/);
  const overlaySource = readFileSync(new URL("./commerce-overlay.tsx", import.meta.url), "utf8");
  assert.match(overlaySource, /dialog\.showModal\(\)/);
  assert.match(overlaySource, /event\.key === "Escape"/);
  assert.match(orderSource, /requestOpener\.current\?\.focus\(\)/);
});

test("주문 상세는 서버 after-sales projection을 새로고침 후에도 표시하고 누락 projection을 허용한다", () => {
  const orderSource = readFileSync(new URL("./commerce-order-detail.tsx", import.meta.url), "utf8");
  const apiSource = readFileSync(new URL("../lib/commerce-final-api.ts", import.meta.url), "utf8");
  assert.match(apiSource, /cancellation\?:/);
  assert.match(apiSource, /rejectionReason\?:string\|null/);
  assert.match(apiSource, /refunds\?:/);
  assert.match(orderSource, /const refunds = order\?\.refunds \?\? \[\]/);
  assert.match(orderSource, /const availableActions = order\?\.availableActions \?\? \[\]/);
  assert.match(orderSource, /취소·반품·환불/);
  assert.match(orderSource, /order\.cancellation\.status/);
  assert.match(orderSource, /order\.return\.status/);
  assert.match(orderSource, /order\.return\.rejectionReason/);
  assert.match(orderSource, /refundLabel\(refund\.status\)/);
  assert.match(orderSource, /availableActions\.includes/);
});

test("관련 상품은 상세 조회와 독립된 loading·retry 상태를 사용한다", () => {
  const productSource = readFileSync(new URL("./product-detail-screen.tsx", import.meta.url), "utf8");
  assert.match(productSource, /kind: "related"/);
  assert.match(productSource, /kind: "complementary"/);
  assert.match(productSource, /RecommendationSection/);
  assert.match(productSource, /PRODUCT_VIEW/);
});

test("Root layout은 공통 Footer를 연결한다", () => {
  const layoutSource = readFileSync(new URL("../app/layout.tsx", import.meta.url), "utf8") + readFileSync(new URL("./customer-shell.tsx", import.meta.url), "utf8");
  const footerSource = readFileSync(new URL("./app-footer.tsx", import.meta.url), "utf8");
  assert.match(layoutSource, /AppFooter/);
  assert.match(footerSource, /\/shipping/);
  assert.match(footerSource, /\/returns/);
  assert.match(footerSource, /\/faq/);
  assert.match(footerSource, /\/notice/);
  assert.match(footerSource, /\/support/);
  assert.match(footerSource, /쇼핑/);
  assert.match(footerSource, /내 쇼핑/);
  assert.match(footerSource, /products\?subscribable=true/);
  assert.match(footerSource, /내 정기배송/);
  assert.match(footerSource, /고객지원/);
});

test("홈은 Commerce 진입 흐름과 인증별 상태를 유지한다", () => {
  assert.match(homeSource, /aria-labelledby="home-title"/);
  assert.match(homeSource, /href="\/products"/);
  assert.match(layoutSource, /\/subscriptions/);
  assert.match(homeSource, /function CompactDiscovery/);
  assert.match(homeSource, /function CompactDiscovery/);
  assert.doesNotMatch(homeSource, /function RoutineValue|className="home-hero"/);
  assert.doesNotMatch(homeSource, /function QuickActions/);
  assert.match(homeSource, /kind: "personalized"/);
  assert.match(homeSource, /RecommendationSection/);
  assert.match(homeSource, /className="home-repeat-band"/);
  assert.match(homeSource, /products\?subscribable=true/);
  assert.match(homeSource, /subscriptionApi\.pets\.list/);
  assert.match(homeSource, /auth\.status === "loading"/);
  assert.match(homeSource, /auth\.status === "anonymous"/);
  assert.match(homeSource, /auth\.status === "authenticated"/);
  assert.match(homeSource, /auth\.status === "error"/);
  assert.match(homeSource, /aria-describedby="recommendation-help"/);
  assert.match(readFileSync(new URL("./recommendation-card.tsx", import.meta.url), "utf8"), /alt=\{`\$\{name\} 상품 이미지`\}/);
  assert.match(globalStylesSource, /@media \(max-width: 767px\)/);
  assert.match(globalStylesSource, /@media \(max-width: 380px\)/);
  assert.match(globalStylesSource, /:focus-visible/);
  assert.match(shoppingStylesSource, /aspect-ratio:1/);
  assert.match(globalStylesSource, /@media \(max-width: 480px\)/);
});

test("카탈로그 카드는 실제 상품 링크와 일관된 이미지·페이지 상태를 제공한다", () => {
  assert.match(productCardSource, /className="product-card-media"/);
  assert.match(productCardSource, /aria-label=\{`\$\{productName\} 상품 상세 보기`\}/);
  assert.match(productCardSource, /catalog-sold-out/);
  assert.doesNotMatch(productCardSource, /product-availability is-unavailable/);
  assert.match(productCardSource, /product\.reviewCount > 0/);
  assert.doesNotMatch(productCardSource, /product\.purchasable \? "구매 가능"/);
  assert.match(productsSource, /className="pagination-row"/);
  assert.match(productsSource, /const \[compareMode, setCompareMode\]/);
  assert.doesNotMatch(productsSource, /userFacingCatalogLabel/);
  assert.match(shoppingStylesSource, /\.catalog-product-card \.product-card-media \{ position: relative; display: grid; aspect-ratio: 1/);
  assert.match(globalStylesSource, /\.pagination-row \{ display: flex/);
});

test("카탈로그·추천·PDP는 반복 상태 소음을 줄이고 서버 사실을 우선 표시한다", () => {
  assert.doesNotMatch(productCardSource, /catalog-rating-empty|아직 리뷰가 없어요/);
  assert.match(productCardSource, /정기배송 가능/);
  assert.match(recommendationSource, /현재 구매 가능한 상품입니다\./);
  assert.match(recommendationSource, /showReason/);
  assert.doesNotMatch(purchasePanelSource, /Your everyday essentials/);
  assert.match(purchasePanelSource, /selection-availability/);
  assert.match(purchasePanelSource, /subscription-callout/);
  assert.match(productDetailSource, /product-subscription-note/);
  assert.doesNotMatch(productDetailSource, /Product guide|At a glance|Product detail|Delivery|Returns/);
});

test("상품 상세는 구매 결정 정보와 정보 section을 분리한다", () => {
  assert.match(productDetailSource, /className="product-purchase-zone"/);
  assert.match(productDetailSource, /className="purchase-price"/);
  assert.match(productDetailSource, /className="product-info-nav"/);
  for (const sectionId of ["product-intro", "product-details", "product-shipping", "product-returns"]) {
    assert.match(productDetailSource, new RegExp(`id="${sectionId}"`));
  }
  assert.match(productDetailSource, /className="mini-product-grid"/);
  assert.match(shoppingStylesSource, /\.catalog-gallery \.product-gallery \{ width:100%; aspect-ratio:1/);
  assert.match(globalStylesSource, /\.product-info-grid/);
});

test("My는 snapshot과 관리 기능을 별도 hierarchy로 제공한다", () => {
  assert.match(mySource, /className="my-dashboard"/);
  assert.match(mySource, /className="my-snapshot-section"/);
  assert.match(mySource, /className="inline-alert" role="alert"/);
  assert.match(mySource, /function ManagementSection/);
  assert.match(mySource, /ManagementSection id="my-shopping-title" title="내 쇼핑"/);
  assert.match(mySource, /ManagementSection id="my-account-title" title="계정 \/ 관리"/);
  assert.match(globalStylesSource, /\.management-grid/);
});

test("주문과 정기배송 empty state는 다음 행동과 사용자 가치를 안내한다", () => {
  assert.match(orderListSource, /아직 주문한 상품이 없어요/);
  assert.match(orderListSource, /상품 둘러보기/);
  assert.match(subscriptionListSource, /아직 시작한 정기배송이 없어요/);
  assert.match(subscriptionListSource, /일반 구매와 달리 선택한 플랜을 정해진 주기에 맞춰 받을 수 있어요/);
  assert.match(subscriptionListSource, /href="\/products"/);
  assert.match(globalStylesSource, /\.empty-state-panel/);
});

test("짧은 화면에서도 app shell이 footer를 viewport 아래에 붙인다", () => {
  assert.match(layoutSource, /<main id="main-content"/);
  assert.match(layoutSource, /<AppFooter compact=\{login\}/);
  assert.match(globalStylesSource, /min-height: ?100dvh/);
  assert.match(globalStylesSource, /\.page-shell \{ flex: 1 0 auto; display: flex; flex-direction: column;/);
  assert.match(shoppingStylesSource, /\.site-footer \{ flex: 0 0 auto;/);
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

test("Checkout cart version and idempotency conflict recovery keep the user in control", () => {
  const checkoutSource = readFileSync(new URL("../app/checkout/page.tsx", import.meta.url), "utf8");
  assert.match(checkoutSource, /setCartVersion\(cartResult\.version\)/);
  assert.match(checkoutSource, /commerceFinalApi\.checkout\(addressId, csrf, key\.current!, couponId \?\? undefined, cartVersion\)/);
  assert.match(checkoutSource, /const identity = `\$\{addressId\}\|\$\{couponId \?\? "none"\}\|\$\{cartVersion\}`/);
  assert.match(checkoutSource, /reason\.code === "CART_CHANGED"/);
  assert.match(checkoutSource, /reason\.code === "IDEMPOTENCY_KEY_CONFLICT"/);
  assert.match(checkoutSource, /Network\/unknown failures keep this key/);
});

test("사용자 화면은 내부 식별자를 노출하지 않는다", () => {
  const successSource = readFileSync(new URL("../app/checkout/success/page.tsx", import.meta.url), "utf8");
  assert.doesNotMatch(subscriptionStartSource, /상품 #\$\{productContext\}|옵션 #/);
  assert.doesNotMatch(subscriptionSource, /Subscription #|\( #/);
  assert.doesNotMatch(successSource, /결제 확인 번호/);
});

test("고객 결제 화면은 내부 provider·test 구현 용어 대신 결과와 다음 행동을 안내한다", () => {
  assert.doesNotMatch(billingSource, /Toss Browser Provider Client|authKey를 이용한 등록 완료/);
  assert.doesNotMatch(checkoutSource, /서버 확인 전 금액|서버가 확정한 금액|서버 확인 뒤 Toss 결제 화면/);
  assert.doesNotMatch(cartSource, /마지막 서버 확인 값|서버가 다시 확인합니다/);
  assert.doesNotMatch(tossPaymentWidgetSource, /Toss Test client key|서버 Toss Test opt-in|<p className="eyebrow">Toss Test/);
  assert.match(billingSource, /현재 결제수단 등록을 완료할 수 없습니다/);
  assert.match(checkoutSource, /예상 결제 금액/);
  assert.match(tossPaymentWidgetSource, /현재 결제를 진행할 수 없습니다/);
});
