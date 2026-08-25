import assert from "node:assert/strict";
import test from "node:test";
import {
  buildLoginHref,
  formatIsoLocalDate,
  formatSubscriptionStatus,
  isInternalDemoLabel,
  sanitizeReturnTo,
  userFacingCatalogLabel,
  validateSubscriptionDraft,
} from "./frontend-utils.ts";

test("로그인 복귀는 승인된 내부 GET 화면만 허용한다", () => {
  for (const route of [
    "/",
    "/products",
    "/products/42",
    "/subscriptions",
    "/subscriptions/new",
    "/subscriptions/7",
    "/orders",
    "/orders/9",
    "/notifications",
    "/wishlist",
    "/cart",
    "/checkout",
    "/addresses",
    "/billing-methods",
    "/my",
  ]) {
    assert.equal(sanitizeReturnTo(route), route);
  }
  assert.equal(sanitizeReturnTo("/products"), "/products");
  assert.equal(sanitizeReturnTo("/products/42"), "/products/42");
  assert.equal(sanitizeReturnTo("/subscriptions/new"), "/subscriptions/new");
  assert.equal(sanitizeReturnTo("/subscriptions/7"), "/subscriptions/7");
  assert.equal(sanitizeReturnTo("/mvp2/subscriptions/new"), "/mvp2/subscriptions/new");
  assert.equal(sanitizeReturnTo("/mvp2/subscriptions/7"), "/mvp2/subscriptions/7");
  assert.equal(sanitizeReturnTo("https://evil.example"), "/products");
  assert.equal(sanitizeReturnTo("//evil.example"), "/products");
  assert.equal(sanitizeReturnTo("/login"), "/products");
  assert.equal(sanitizeReturnTo("/admin/users"), "/products");
  assert.equal(sanitizeReturnTo("/unknown"), "/products");
  assert.equal(sanitizeReturnTo("/orders/0"), "/products");
  assert.equal(sanitizeReturnTo("/products/-1"), "/products");
  assert.equal(sanitizeReturnTo("/my?next=/admin"), "/products");
  assert.equal(sanitizeReturnTo("/subscriptions/0"), "/products");
  assert.equal(
    buildLoginHref("/subscriptions/7"),
    "/login?returnTo=%2Fsubscriptions%2F7",
  );
});

test("ISO local date는 timezone 변환 없이 표시한다", () => {
  assert.equal(formatIsoLocalDate("2026-07-14"), "2026. 7. 14.");
  assert.equal(formatIsoLocalDate("invalid"), "invalid");
});

test("구독 상태는 사용자 표현으로 표시한다", () => {
  assert.equal(formatSubscriptionStatus("ACTIVE"), "이용 중");
  assert.equal(formatSubscriptionStatus("PAUSED"), "일시정지");
  assert.equal(formatSubscriptionStatus("CANCELED"), "해지됨");
  assert.equal(formatSubscriptionStatus("UNKNOWN"), "UNKNOWN");
});

test("QA·Demo 상품명은 사용자 노출 문구로 대체한다", () => {
  assert.equal(isInternalDemoLabel("V2 concurrent product"), true);
  assert.equal(isInternalDemoLabel("v2-concurrent-fixture"), true);
  assert.equal(isInternalDemoLabel("무향 벤토나이트 모래"), false);
  assert.equal(userFacingCatalogLabel("test option", "상품 옵션"), "상품 옵션");
  assert.equal(userFacingCatalogLabel("2kg", "상품 옵션"), "2kg");
});

test("구독 입력은 수량 경계와 서버 제공 선택지를 검증한다", () => {
  assert.deepEqual(
    validateSubscriptionDraft(
      { skuId: 10, quantity: "1", deliveryCycleWeeks: 2 },
      [10],
      [2, 4, 8],
    ),
    {},
  );
  assert.deepEqual(
    validateSubscriptionDraft(
      { skuId: 10, quantity: "10", deliveryCycleWeeks: 8 },
      [10],
      [2, 4, 8],
    ),
    {},
  );
  assert.deepEqual(
    validateSubscriptionDraft(
      { skuId: null, quantity: "11", deliveryCycleWeeks: 6 },
      [10],
      [2, 4, 8],
    ),
    {
      skuId: "구독할 옵션을 선택해 주세요.",
      quantity: "수량은 최대 10개까지 선택할 수 있습니다.",
      deliveryCycleWeeks: "배송 주기는 제공된 선택지 중에서 선택해 주세요.",
    },
  );
});
