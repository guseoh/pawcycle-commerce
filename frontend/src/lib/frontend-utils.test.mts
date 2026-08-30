import assert from "node:assert/strict";
import test from "node:test";
import {
  buildLoginHref,
  formatIsoLocalDate,
  formatPetType,
  formatScheduleStatus,
  formatSubscriptionStatus,
  subscriptionIssueCopy,
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
    "/checkout/success",
    "/addresses",
    "/billing-methods",
    "/my",
  ]) {
    assert.equal(sanitizeReturnTo(route), route);
  }
  assert.equal(buildLoginHref("/checkout/success"), "/login?returnTo=%2Fcheckout%2Fsuccess");
  assert.equal(sanitizeReturnTo("/checkout/success?paymentKey=secret"), "/products");
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
  assert.equal(formatSubscriptionStatus("ACTIVE"), "진행 중");
  assert.equal(formatSubscriptionStatus("PAUSED"), "일시정지됨");
  assert.equal(formatSubscriptionStatus("CANCELED"), "종료됨");
  assert.equal(formatSubscriptionStatus("UNKNOWN"), "상태 확인 필요");
});

test("정기배송 상태·반려동물·이슈 formatter는 알 수 없는 값도 안전하게 처리한다", () => {
  assert.equal(formatPetType("DOG"), "개");
  assert.equal(formatPetType("CAT"), "고양이");
  assert.equal(formatPetType("BIRD"), "반려동물");
  assert.equal(formatSubscriptionStatus("UNKNOWN"), "상태 확인 필요");
  assert.equal(formatScheduleStatus("SCHEDULED"), "배송 예정");
  assert.equal(formatScheduleStatus("SKIPPED"), "건너뜀");
  assert.equal(formatScheduleStatus("HELD"), "다음 배송 확인 필요");
  assert.equal(formatScheduleStatus("CANCELED"), "배송 취소");
  assert.equal(formatScheduleStatus("UNKNOWN"), "배송 상태 확인 필요");
  assert.equal(subscriptionIssueCopy("SHIPPING_ADDRESS_REQUIRED"), "배송지를 확인해 주세요.");
  assert.equal(subscriptionIssueCopy("BILLING_METHOD_REQUIRED"), "결제수단 등록 상태를 확인해 주세요.");
  assert.equal(subscriptionIssueCopy("PAYMENT_SUPPORT_REQUIRED"), "결제 확인을 위해 고객지원이 필요해요.");
  assert.equal(subscriptionIssueCopy("STOCK_UNAVAILABLE"), "이번 배송 추가 상품의 재고를 확인해 주세요.");
  assert.equal(subscriptionIssueCopy("UNKNOWN"), "정기배송을 계속하려면 확인이 필요한 항목이 있어요.");
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
