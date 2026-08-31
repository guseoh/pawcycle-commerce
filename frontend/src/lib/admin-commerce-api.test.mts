import assert from "node:assert/strict";
import test from "node:test";
import { adminCommerceApi as api } from "./admin-commerce-api.ts";

function capture(response: Response) {
  const original = globalThis.fetch;
  const calls: Array<{ path: string; method: string; csrf: string | null; body: unknown }> = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ path: String(input), method: init?.method ?? "GET", csrf: new Headers(init?.headers).get("X-CSRF-TOKEN"), body: init?.body ? JSON.parse(String(init.body)) : undefined });
    return response.clone();
  }) as typeof fetch;
  return { calls, restore: () => { globalThis.fetch = original; } };
}

test("admin commerce reads use the existing resource endpoints", async () => {
  const mock = capture(Response.json([]));
  try {
    await api.inventories(); await api.coupons(); await api.membershipGrades(); await api.orders(); await api.auditLogs(); await api.order(9);
    assert.deepEqual(mock.calls.map((call) => [call.path, call.method, call.csrf, call.body]), [
      ["/api/admin/inventories", "GET", null, undefined],
      ["/api/admin/coupons", "GET", null, undefined],
      ["/api/admin/membership-grades", "GET", null, undefined],
      ["/api/admin/orders", "GET", null, undefined],
      ["/api/admin/audit-logs", "GET", null, undefined],
      ["/api/admin/orders/9", "GET", null, undefined],
    ]);
  } finally { mock.restore(); }
});

test("admin commerce mutations keep CSRF and approved request bodies", async () => {
  const mock = capture(Response.json({}));
  try {
    await api.adjustInventory(4, -2, "inventory-csrf");
    await api.createCoupon({ name: "신규 쿠폰" }, "coupon-csrf");
    await api.updateCoupon(7, { name: "수정 쿠폰" }, "coupon-update-csrf");
    await api.issueCoupon(7, 3, "issue-csrf");
    await api.createMembershipGrade({ code: "PLUS" }, "grade-csrf");
    await api.evaluateMembership(3, "evaluate-csrf");
    assert.deepEqual(mock.calls.map((call) => [call.path, call.method, call.csrf, call.body]), [
      ["/api/admin/inventories/4/adjustments", "POST", "inventory-csrf", { delta: -2 }],
      ["/api/admin/coupons", "POST", "coupon-csrf", { name: "신규 쿠폰" }],
      ["/api/admin/coupons/7", "PATCH", "coupon-update-csrf", { name: "수정 쿠폰" }],
      ["/api/admin/coupons/7/issues", "POST", "issue-csrf", { memberId: 3 }],
      ["/api/admin/membership-grades", "POST", "grade-csrf", { code: "PLUS" }],
      ["/api/admin/members/3/membership/evaluate", "POST", "evaluate-csrf", {}],
    ]);
  } finally { mock.restore(); }
});
