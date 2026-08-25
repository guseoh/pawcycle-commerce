import assert from "node:assert/strict";
import test from "node:test";
import { isTossTestClientKey, validateTossSuccess } from "./toss-payment.ts";

test("Toss Test client key is opt-in and rejects live or missing values", () => {
  assert.equal(isTossTestClientKey(undefined), false);
  assert.equal(isTossTestClientKey("live_ck_example"), false);
  assert.equal(isTossTestClientKey("test_ck_example"), true);
});

test("success redirect validation uses the server checkout context, not URL amount", () => {
  const expected = { providerOrderId: "TOSS-provider", orderName: "사료", amount: 1500 };
  assert.equal(validateTossSuccess({ paymentKey: "pay-key", providerOrderId: "TOSS-provider", urlAmount: "1500" }, expected), null);
  assert.equal(validateTossSuccess({ paymentKey: "pay-key", providerOrderId: "TOSS-provider", urlAmount: "1" }, expected), "결제 금액을 확인할 수 없습니다.");
  assert.equal(validateTossSuccess({ paymentKey: "pay-key", providerOrderId: "TOSS-other", urlAmount: "1500" }, expected), "결제 주문 정보를 확인할 수 없습니다.");
});
