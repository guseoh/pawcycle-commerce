import assert from "node:assert/strict";
import test from "node:test";
import { ApiError } from "./http/api-error.ts";
import { orderApi } from "./order-api.ts";
import type { OrderDetail } from "./commerce-types.ts";

const order: Omit<OrderDetail, "return"> = {
  orderId: 7, orderNumber: "ORDER-7", source: "ONE_TIME", status: "PAID",
  originalAmount: 15000, discountAmount: 1000, shippingFee: 3000, paymentAmount: 17000,
  recipientName: "fixture", recipientPhone: null, postalCode: "12345", addressLine1: "fixture", addressLine2: null,
  createdAt: "2026-10-18T10:00:00.123456", paidAt: "2026-10-18T10:01:00.654321",
  items: [{ skuId: 3, skuCodeSnapshot: "SKU-3", productNameSnapshot: "사료", skuNameSnapshot: "1kg", unitPrice: 15000, quantity: 1, lineAmount: 15000 }],
  payment: { paymentId: 8, type: "NORMAL", provider: "TOSS", status: "SUCCEEDED", amount: 17000, attemptNo: 1, providerStatus: "DONE" },
  delivery: { deliveryId: 9, status: "DELIVERED", carrierCode: "CJ", trackingNumber: "fixture", shippedAt: "2026-10-18T12:00:00", deliveredAt: "2026-10-19T12:00:00" },
  cancellation: { cancellationId: 10, status: "REJECTED", reason: "fixture", requestedAt: "2026-10-18T11:00:00", completedAt: null },
  refunds: [{ refundId: 11, source: "RETURN", status: "READY", attemptNo: 1, amount: 15000 }],
  availableActions: ["REQUEST_RETURN"],
};

function returned(status: string) {
  return {
    returnId: 12, status, reason: "포장 파손",
    rejectionReason: status === "REJECTED" ? "반품 조건에 맞지 않습니다." : null,
    restock: status === "COMPLETED" ? true : status === "REJECTED" ? false : null,
    requestedAt: "2026-10-19T13:00:00.123456",
    receivedAt: status === "COMPLETED" ? "2026-10-20T09:00:00.654321" : null,
    completedAt: status === "COMPLETED" ? "2026-10-20T10:00:00.000001" : null,
  };
}

async function detailResponse(body: unknown) {
  const original = globalThis.fetch;
  globalThis.fetch = (async () => Response.json(body)) as typeof fetch;
  try { return await orderApi.detail("7"); } finally { globalThis.fetch = original; }
}

for (const status of ["REQUESTED", "APPROVED", "REJECTED", "COMPLETED"]) {
  test(`order detail maps Backend returnRequest ${status} without losing nested or unrelated fields`, async () => {
    const returnRequest = returned(status);
    assert.deepEqual(await detailResponse({ ...order, returnRequest }), { ...order, return: returnRequest });
  });
}

test("order detail preserves null and omitted return projections", async () => {
  assert.deepEqual(await detailResponse({ ...order, returnRequest: null }), { ...order, return: null });
  assert.deepEqual(await detailResponse(order), order);
});

test("order detail accepts a canonical return-only response including explicit null", async () => {
  const canonical = { ...order, return: returned("REJECTED") };
  assert.deepEqual(await detailResponse(canonical), canonical);
  assert.deepEqual(await detailResponse({ ...order, return: null }), { ...order, return: null });
});

test("an existing return field takes precedence over returnRequest by presence, including null", async () => {
  const canonical = returned("APPROVED");
  assert.deepEqual(await detailResponse({ ...order, return: canonical, returnRequest: null }), { ...order, return: canonical });
  assert.deepEqual(await detailResponse({ ...order, return: null, returnRequest: canonical }), { ...order, return: null });
  assert.deepEqual(await detailResponse({ ...order, return: canonical, returnRequest: canonical }), { ...order, return: canonical });
});

test("order detail retains its encoded GET path and shared authenticated request policy", async () => {
  const original = globalThis.fetch;
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ path: String(input), init });
    return Response.json({ ...order, returnRequest: null });
  }) as typeof fetch;
  try {
    await orderApi.detail("7/part ?");
    assert.equal(calls.length, 1);
    assert.equal(calls[0].path, "/api/orders/7%2Fpart%20%3F");
    assert.equal(calls[0].init?.method ?? "GET", "GET");
    assert.equal(calls[0].init?.credentials, "same-origin");
    assert.equal(calls[0].init?.cache, "no-store");
    assert.equal(new Headers(calls[0].init?.headers).get("Accept"), "application/json");
    assert.equal(calls[0].init?.body, undefined);
  } finally { globalThis.fetch = original; }
});

for (const [status, code] of [[401, "UNAUTHORIZED"], [404, "ORDER_NOT_FOUND"]] as const) {
  test(`order detail preserves ${status} ${code} errors`, async () => {
    const original = globalThis.fetch;
    globalThis.fetch = (async () => Response.json({ code, message: "fixture", fieldErrors: [] }, { status })) as typeof fetch;
    try {
      await assert.rejects(orderApi.detail("7"), (error: unknown) => error instanceof ApiError && error.status === status && error.code === code);
    } finally { globalThis.fetch = original; }
  });
}

test("list, cancellation and return creation keep their original request and response contracts", async () => {
  const original = globalThis.fetch;
  const summary = [{ orderId: 7, orderNumber: order.orderNumber, status: order.status, paymentAmount: order.paymentAmount, createdAt: order.createdAt }];
  const cancellation = { cancellationId: 10, status: "REQUESTED", reason: "fixture", requestedAt: order.createdAt, completedAt: null };
  const returnRequest = { ...returned("REQUESTED"), decidedAt: null };
  const bodies = [summary, cancellation, returnRequest];
  const calls: Array<{ path: string; method: string; body: string; csrf: string | null }> = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ path: String(input), method: init?.method ?? "GET", body: String(init?.body ?? ""), csrf: new Headers(init?.headers).get("X-CSRF-TOKEN") });
    return Response.json(bodies[calls.length - 1]);
  }) as typeof fetch;
  try {
    assert.deepEqual(await orderApi.list(), summary);
    assert.deepEqual(await orderApi.cancellation("7", "fixture", "fixture-csrf"), cancellation);
    assert.deepEqual(await orderApi.returnRequest("7", "포장 파손", "fixture-csrf"), returnRequest);
    assert.deepEqual(calls, [
      { path: "/api/orders", method: "GET", body: "", csrf: null },
      { path: "/api/orders/7/cancellations", method: "POST", body: JSON.stringify({ reason: "fixture" }), csrf: "fixture-csrf" },
      { path: "/api/orders/7/returns", method: "POST", body: JSON.stringify({ reason: "포장 파손" }), csrf: "fixture-csrf" },
    ]);
  } finally { globalThis.fetch = original; }
});
