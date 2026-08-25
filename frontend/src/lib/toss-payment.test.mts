import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { isTossTestClientKey, validateTossSuccess } from "./toss-payment.ts";

const successSource = readFileSync(new URL("../app/checkout/success/page.tsx", import.meta.url), "utf8");
const widgetSource = readFileSync(new URL("../components/toss-payment-widget.tsx", import.meta.url), "utf8");

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

test("success redirect requires complete callback values before confirm", () => {
  const expected = { providerOrderId: "TOSS-provider", orderName: "사료", amount: 1500 };
  assert.equal(validateTossSuccess({ paymentKey: null, providerOrderId: "TOSS-provider", urlAmount: "1500" }, expected), "결제 인증 결과가 완전하지 않습니다.");
  assert.equal(validateTossSuccess({ paymentKey: "pay-key", providerOrderId: null, urlAmount: "1500" }, expected), "결제 인증 결과가 완전하지 않습니다.");
});

test("Toss browser flow requires backend opt-in and Strict Mode reuses the in-flight confirm", () => {
  assert.match(widgetSource, /checkout\.tossTestEnabled && isTossTestClientKey\(clientKey\)/);
  assert.match(successSource, /confirmPromiseRef/);
  assert.match(successSource, /confirmKeyRef/);
  assert.doesNotMatch(successSource, /attemptedRef/);
  assert.match(successSource, /saveTossSuccessCallback/);
  assert.match(successSource, /window\.history\.replaceState/);
  assert.match(successSource, /const hasCallback = Boolean\(callbackFromUrl \?\? readTossSuccessCallback\(\)\)/);
  assert.doesNotMatch(successSource, /setCallbackReady|setHasCallback/);
  assert.match(successSource, /buildLoginHref\("\/checkout\/success"\)/);
});
