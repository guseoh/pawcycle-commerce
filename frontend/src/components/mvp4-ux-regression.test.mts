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

test("장바구니 수량 입력은 최종 draft만 적용한다", () => {
  const typedDrafts = ["1", "12"];
  const pastedDrafts = ["12"];

  assert.deepEqual(typedDrafts.map(cartQuantityError), [null, null]);
  assert.deepEqual(pastedDrafts.map(cartQuantityError), [null]);
  assert.deepEqual([cartQuantityForUpdate(typedDrafts.at(-1)!)], [12]);
  assert.deepEqual([cartQuantityForUpdate(pastedDrafts.at(-1)!)], [12]);
  assert.match(cartSource, /onChange=\{\(event\) => updateDraft\(item\.skuId, event\.target\.value\)\}/);
  assert.match(cartSource, /onClick=\{\(\) => void applyQuantity\(item\)\}/);
  assert.equal((cartSource.match(/commerceFinalApi\.updateCart/g) ?? []).length, 1);
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
