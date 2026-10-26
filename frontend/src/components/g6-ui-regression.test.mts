import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");
const accountCss = source("../app/account-visual-v2.css");

test("G6-05: order actions follow status and keep the server action boundary", () => {
  const order = source("./commerce-order-detail.tsx");
  assert.ok(order.indexOf('className="section-card order-actions"') > order.indexOf('className="section-card order-overview"'));
  assert.ok(order.indexOf('className="section-card order-actions"') < order.indexOf('aria-labelledby="order-items-title"'));
  assert.match(order, /availableActions\.includes\("REQUEST_CANCELLATION"\)/);
  assert.match(order, /availableActions\.includes\("REQUEST_RETURN"\)/);
  assert.match(order, /onClick=\{\(\) => void quickReorder\(\)\}/);
  assert.equal((order.match(/onClick=\{\(\) => openRequest\(/g) ?? []).length, 2);
});

test("G6-04: plan names and selection state identify the explicit selection", () => {
  const start = source("./subscription-start.tsx");
  assert.doesNotMatch(start, /상세 선택/);
  assert.match(start, /aria-label=\{`\$\{plan\.planName\}/);
  assert.match(start, /aria-pressed=\{selectedPlan\?\.planVersionId === plan\.planVersionId\}/);
  assert.match(start, /onClick=\{\(\) => void selectPlan\(plan\)\}/);
});

test("G6-04: current, next and pending facts stay separate without inferred dates", () => {
  const detail = source("./subscription-detail.tsx");
  const comparison = detail.slice(detail.indexOf('<section className="subscription-state-comparison"'), detail.indexOf('<div className="subscription-main">'));
  assert.ok(comparison.indexOf("subscription.currentSnapshot") < comparison.indexOf("subscription.nextDelivery"));
  assert.ok(comparison.indexOf("subscription.nextDelivery") < comparison.indexOf("subscription.pendingChange"));
  assert.match(comparison, /formatIsoLocalDate\(subscription\.pendingChange\.appliesOn\)/);
  assert.match(comparison, /예정된 다음 주문 회차가 없습니다/);
  assert.match(comparison, /적용 예정인 변경이 없습니다/);
  assert.match(detail, /hasAction\("RESCHEDULE_NEXT"\)/);
  assert.match(detail, /command\(subscription\.subscriptionId, command, body, csrf, etag, commandKey\.key\)/);
});

test("G6-03: anonymous notices stay neutral while failures remain alerts", () => {
  const states = source("./async-state.tsx");
  const neutral = states.slice(states.indexOf("export function AuthRequiredState"), states.indexOf("export function ErrorState"));
  assert.doesNotMatch(neutral, /role="alert"|state-panel-error/);
  assert.match(states.slice(states.indexOf("export function ErrorState")), /role="alert"/);
  for (const file of ["../app/my/page.tsx", "../app/checkout/page.tsx", "../app/cart/page.tsx", "../app/addresses/page.tsx", "./pet-profile-screen.tsx"]) {
    const page = source(file);
    assert.match(page, /auth\.status === "anonymous"\) return <AuthRequiredState/);
    assert.match(page, /auth\.status === "error"\) return <ErrorState/);
    assert.match(page, /build(?:Address)?LoginHref\(/);
  }
});

test("G6-03: Checkout explains distinct blockers beside the unchanged explicit action", () => {
  const checkout = source("../app/checkout/page.tsx");
  assert.match(checkout, /const checkoutBlockReason = !cart\.length/);
  assert.match(checkout, /: !addressId \? "주문할 배송지를 선택해 주세요\."/);
  assert.match(checkout, /aria-describedby=\{checkoutBlockReason \? "checkout-block-reason" : undefined\}/);
  assert.match(checkout, /disabled=\{!cart\.length \|\| !addressId \|\| unavailable\.length > 0 \|\| busy\}/);
  assert.equal((checkout.match(/checkoutApi\.create\(/g) ?? []).length, 1);
});

test("G6-02: subscription and billing facts preserve server values and pagination", () => {
  const list = source("./subscription-list.tsx");
  const billing = source("../app/billing-methods/page.tsx");
  assert.match(list, /<dl className="subscription-row-fact"><dt>다음 주문 예정일<\/dt><dd>/);
  assert.match(list, /subscription\.nextScheduledDate \? formatIsoLocalDate\(subscription\.nextScheduledDate\) : "예정 없음"/);
  assert.match(list, /\(page \+ 1\) \* result\.size >= result\.totalElements/);
  assert.match(billing, /<dt>등록 상태<\/dt><dd>\{status\.registered/);
  assert.match(billing, /<dt>서비스 상태<\/dt><dd>\{status\.configured/);
  assert.match(billing, /aria-describedby=\{!status\.configured \? "billing-unavailable" : undefined\}/);
  assert.match(accountCss, /\.subscription-row-fact\s*\{[^}]*gap:6px/);
});

test("G6-01: address actions remain outside the scrolling fields and failures stay in the dialog", () => {
  const address = source("../app/addresses/page.tsx");
  const body = address.indexOf('className="address-dialog-body form-section"');
  const actions = address.indexOf('className="button-row address-dialog-actions"');
  assert.ok(body > 0 && actions > body);
  assert.match(address.slice(body, actions), /error.*role="alert"/);
  assert.match(address.slice(body, actions), /disabled=\{busy\} required=\{required\}/);
  assert.match(accountCss, /\.address-dialog-body\s*\{[^}]*min-height:0;[^}]*overflow-y:auto/);
  assert.match(accountCss, /\.address-dialog-actions\s*\{[^}]*flex:none/);
  assert.match(address, /onCancel=.*closeForm\(\)/);
  assert.match(address, /formOpener\.current\?\.focus\(\)/);
});

test("G6-01: pet edit focus enters the input and returns only on explicit completion", () => {
  const pet = source("./pet-profile-screen.tsx");
  assert.match(pet, /if \(editing\) editNameRef\.current\?\.focus\(\)/);
  assert.match(pet, /restoreEditFocus\.current = petId/);
  assert.match(pet, /node && restoreEditFocus\.current === pet\.petId/);
  assert.match(pet, /onCancel=\{\(\) => finishEdit\(pet\.petId\)\}/);
  assert.equal((pet.match(/finishEdit\(pet\.petId\)/g) ?? []).length, 3);
  assert.match(pet, /subscriptionApi\.pets\.patch\(pet\.petId, changes, csrf\)/);
});
