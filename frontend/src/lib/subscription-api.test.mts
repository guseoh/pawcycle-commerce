import assert from "node:assert/strict";
import test from "node:test";
import { newIdempotencyKey, subscriptionApi, type SubscriptionDetail } from "./subscription-api.ts";

test("subscription mutating actions receive a non-empty idempotency key", () => {
  const key = newIdempotencyKey();
  assert.ok(key.length > 0);
  assert.match(key, /^[A-Za-z0-9._-]+$/);
});

test("subscription command preserves protocol headers and reuses the caller's action key on retry", async () => {
  const originalFetch = globalThis.fetch;
  const requests: RequestInit[] = [];
  globalThis.fetch = (async (_input: string | URL | Request, init?: RequestInit) => {
    requests.push(init ?? {});
    return new Response(JSON.stringify({ subscriptionId: 7 }), {
      status: 200,
      headers: { "Content-Type": "application/json", ETag: "\"4\"", Location: "/api/subscriptions/7", "Idempotency-Replayed": "true" },
    });
  }) as typeof fetch;
  try {
    const key = "retry-key";
    const first = await subscriptionApi.subscriptions.command(7, "pause", {}, "csrf", "\"3\"", key);
    const second = await subscriptionApi.subscriptions.command(7, "pause", {}, "csrf", "\"3\"", key);
    assert.equal(first.etag, "\"4\"");
    assert.equal(first.location, "/api/subscriptions/7");
    assert.equal(first.replayed, true);
    assert.equal(second.replayed, true);
    assert.equal((requests[0].headers as Record<string, string>)["If-Match"], "\"3\"");
    assert.equal((requests[0].headers as Record<string, string>)["Idempotency-Key"], key);
    assert.equal((requests[1].headers as Record<string, string>)["Idempotency-Key"], key);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("API-004 subscription JSON fixture remains assignable to the frontend contract", () => {
  const fixture = {
    subscriptionId: 7,
    pet: { petId: 3, name: "보리", petType: "DOG", breed: null, weightKg: null, profileComplete: false },
    status: "ACTIVE",
    version: 4,
    currentSnapshot: { planVersionId: 12, packagePriceKrw: 24900, deliveryCycleWeeks: 4, items: [{ skuId: 8, quantity: 2 }] },
    nextScheduledDate: "2026-08-30",
    pendingSnapshot: null,
    schedules: { page: 0, size: 20, totalElements: 1, items: [{ scheduleId: 31, scheduledDate: "2026-08-30", status: "SCHEDULED", effectiveSnapshotId: null }] },
    commandHistory: { page: 0, size: 20, totalElements: 1, items: [{ commandType: "CHANGE_PLAN", result: "SUCCEEDED", occurredAt: "2026-08-02T12:00:00+09:00" }] },
  } satisfies SubscriptionDetail;
  assert.equal(fixture.currentSnapshot.packagePriceKrw, 24900);
  assert.equal(fixture.schedules.items[0].effectiveSnapshotId, null);
  assert.equal(fixture.commandHistory.items[0].result, "SUCCEEDED");
});

test("MVP4 reschedule and delivery-cycle commands keep ETag and idempotency headers", async () => {
  const originalFetch = globalThis.fetch; const paths: string[] = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => { paths.push(String(input)); assert.equal((init?.headers as Record<string, string>)["If-Match"], "\"8\""); return new Response("{}", { status: 200 }); }) as typeof fetch;
  try {
    await subscriptionApi.subscriptions.command(7, "reschedule-next", { scheduledDate: "2026-09-10" }, "csrf", "\"8\"", "reschedule-key");
    await subscriptionApi.subscriptions.command(7, "change-delivery-cycle", { deliveryCycleWeeks: 8 }, "csrf", "\"8\"", "cycle-key");
    assert.match(paths[0], /reschedule-next/); assert.match(paths[1], /change-delivery-cycle/);
  } finally { globalThis.fetch = originalFetch; }
});

test("반려동물 PATCH는 허용 필드와 explicit null을 그대로 전송한다", async () => {
  const originalFetch = globalThis.fetch;
  const calls: { path: string; body: unknown; csrf: string | null }[] = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ path: String(input), body: JSON.parse(String(init?.body)), csrf: new Headers(init?.headers).get("X-CSRF-TOKEN") });
    return Response.json({ petId: 4 });
  }) as typeof fetch;
  try {
    await subscriptionApi.pets.patch(4, { breed: null, weightKg: null }, "pet-csrf");
    assert.deepEqual(calls, [{ path: "/api/pets/4", body: { breed: null, weightKg: null }, csrf: "pet-csrf" }]);
  } finally { globalThis.fetch = originalFetch; }
});

test("cycle suggestion과 next-delivery add-on commands는 canonical path와 보호 헤더를 사용한다", async () => {
  const originalFetch = globalThis.fetch;
  const calls: { path: string; body: unknown; headers: Headers }[] = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ path: String(input), body: init?.body ? JSON.parse(String(init.body)) : undefined, headers: new Headers(init?.headers) });
    return Response.json({ subscriptionId: 7, suggestion: null });
  }) as typeof fetch;
  try {
    await subscriptionApi.subscriptions.cycleSuggestion(7);
    await subscriptionApi.subscriptions.command(7, "set-next-delivery-addon", { skuId: 88, quantity: 2 }, "csrf", "\"4\"", "addon-set");
    await subscriptionApi.subscriptions.command(7, "remove-next-delivery-addon", { skuId: 88 }, "csrf", "\"5\"", "addon-remove");
    assert.deepEqual(calls.map((call) => [call.path, call.body]), [
      ["/api/subscriptions/7/cycle-suggestion", undefined],
      ["/api/subscriptions/7/commands/set-next-delivery-addon", { skuId: 88, quantity: 2 }],
      ["/api/subscriptions/7/commands/remove-next-delivery-addon", { skuId: 88 }],
    ]);
    assert.equal(calls[1].headers.get("If-Match"), "\"4\"");
    assert.equal(calls[1].headers.get("Idempotency-Key"), "addon-set");
    assert.equal(calls[2].headers.get("X-CSRF-TOKEN"), "csrf");
  } finally { globalThis.fetch = originalFetch; }
});
