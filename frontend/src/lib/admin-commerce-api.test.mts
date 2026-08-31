import assert from "node:assert/strict";
import test from "node:test";
import { adminCommerceApi as api, toAdminCouponInput, toAdminCouponRequest } from "./admin-commerce-api.ts";

function capture(response: Response) {
  const original = globalThis.fetch;
  const calls: Array<{ path: string; method: string; csrf: string | null; body: unknown }> = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ path: String(input), method: init?.method ?? "GET", csrf: new Headers(init?.headers).get("X-CSRF-TOKEN"), body: init?.body ? JSON.parse(String(init.body)) : undefined });
    return response.clone();
  }) as typeof fetch;
  return { calls, restore: () => { globalThis.fetch = original; } };
}

const createdCoupon = {
  name: "신규 쿠폰",
  discountType: "FIXED_AMOUNT" as const,
  discountValue: 3000,
  minimumOrderAmount: 20000,
  maximumDiscountAmount: null,
  validFrom: "2026-08-31T12:30",
  validUntil: "2026-09-30T23:59",
  active: true,
};

const updatedCoupon = {
  name: "수정 쿠폰",
  discountType: "PERCENTAGE" as const,
  discountValue: 10,
  minimumOrderAmount: 15000,
  maximumDiscountAmount: 5000,
  validFrom: "2026-09-01T00:00",
  validUntil: "2026-10-01T00:00",
  active: false,
};

test("admin coupon form conversion preserves ISO LocalDateTime contract", () => {
  assert.deepEqual(toAdminCouponRequest({
    name: "  신규 쿠폰  ",
    discountType: "FIXED_AMOUNT",
    discountValue: "3000",
    minimumOrderAmount: "20000",
    maximumDiscountAmount: "",
    validFrom: "2026-08-31T12:30",
    validUntil: "2026-09-30T23:59",
    active: true,
  }), createdCoupon);

  assert.deepEqual(toAdminCouponInput({ couponId: 7, ...updatedCoupon, validFrom: "2026-09-01 00:00:00", validUntil: "2026-10-01T00:00:00" }), {
    name: "수정 쿠폰",
    discountType: "PERCENTAGE",
    discountValue: "10",
    minimumOrderAmount: "15000",
    maximumDiscountAmount: "5000",
    validFrom: "2026-09-01T00:00",
    validUntil: "2026-10-01T00:00",
    active: false,
  });
});

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

test("admin commerce mutations keep CSRF and full coupon request bodies", async () => {
  const mock = capture(Response.json({}));
  try {
    await api.adjustInventory(4, -2, "inventory-csrf");
    await api.createCoupon(createdCoupon, "coupon-csrf");
    await api.updateCoupon(7, updatedCoupon, "coupon-update-csrf");
    await api.issueCoupon(7, 3, "issue-csrf");
    await api.createMembershipGrade({ code: "PLUS" }, "grade-csrf");
    await api.evaluateMembership(3, "evaluate-csrf");
    assert.deepEqual(mock.calls.map((call) => [call.path, call.method, call.csrf, call.body]), [
      ["/api/admin/inventories/4/adjustments", "POST", "inventory-csrf", { delta: -2 }],
      ["/api/admin/coupons", "POST", "coupon-csrf", createdCoupon],
      ["/api/admin/coupons/7", "PATCH", "coupon-update-csrf", updatedCoupon],
      ["/api/admin/coupons/7/issues", "POST", "issue-csrf", { memberId: 3 }],
      ["/api/admin/membership-grades", "POST", "grade-csrf", { code: "PLUS" }],
      ["/api/admin/members/3/membership/evaluate", "POST", "evaluate-csrf", {}],
    ]);
  } finally { mock.restore(); }
});
