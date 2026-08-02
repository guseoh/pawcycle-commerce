import assert from "node:assert/strict";
import test from "node:test";
import { newIdempotencyKey, v2Api } from "./v2-api.ts";

test("V2 mutating actions receive a non-empty idempotency key", () => {
  const key = newIdempotencyKey();
  assert.ok(key.length > 0);
  assert.match(key, /^[A-Za-z0-9._-]+$/);
});

test("V2 command preserves protocol headers and reuses the caller's action key on retry", async () => {
  const originalFetch = globalThis.fetch;
  const requests: RequestInit[] = [];
  globalThis.fetch = (async (_input: string | URL | Request, init?: RequestInit) => {
    requests.push(init ?? {});
    return new Response(JSON.stringify({ subscriptionId: 7 }), {
      status: 200,
      headers: { "Content-Type": "application/json", ETag: "\"4\"", Location: "/api/v2/subscriptions/7", "Idempotency-Replayed": "true" },
    });
  }) as typeof fetch;
  try {
    const key = "retry-key";
    const first = await v2Api.subscriptions.command(7, "pause", {}, "csrf", "\"3\"", key);
    const second = await v2Api.subscriptions.command(7, "pause", {}, "csrf", "\"3\"", key);
    assert.equal(first.etag, "\"4\"");
    assert.equal(first.location, "/api/v2/subscriptions/7");
    assert.equal(first.replayed, true);
    assert.equal(second.replayed, true);
    assert.equal((requests[0].headers as Record<string, string>)["If-Match"], "\"3\"");
    assert.equal((requests[0].headers as Record<string, string>)["Idempotency-Key"], key);
    assert.equal((requests[1].headers as Record<string, string>)["Idempotency-Key"], key);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
