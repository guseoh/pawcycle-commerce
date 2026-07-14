import assert from "node:assert/strict";
import test from "node:test";
import {
  buildLoginHref,
  formatIsoLocalDate,
  sanitizeReturnTo,
  validateSubscriptionDraft,
} from "./frontend-utils.ts";

test("로그인 복귀는 승인된 내부 GET 화면만 허용한다", () => {
  assert.equal(sanitizeReturnTo("/products"), "/products");
  assert.equal(sanitizeReturnTo("/products/42"), "/products/42");
  assert.equal(sanitizeReturnTo("/subscriptions/7"), "/subscriptions/7");
  assert.equal(sanitizeReturnTo("https://evil.example"), "/products");
  assert.equal(sanitizeReturnTo("//evil.example"), "/products");
  assert.equal(sanitizeReturnTo("/login"), "/products");
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
