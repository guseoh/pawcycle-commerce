import assert from "node:assert/strict";
import test from "node:test";
import { ApiError } from "./api-error.ts";
import { requestJson, requestVoid, requestWithMetadata } from "./client.ts";

test("shared client parses JSON responses and preserves default request policy", async () => {
  const original = globalThis.fetch;
  let capturedRequest: Request | undefined;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    capturedRequest = new Request(String(input), init);
    return Response.json({ ok: true });
  }) as typeof fetch;
  try {
    assert.deepEqual(await requestJson<{ ok: boolean }>("http://localhost/api/example"), { ok: true });
    assert.ok(capturedRequest);
    assert.equal(capturedRequest.headers.get("Accept"), "application/json");
    assert.equal(capturedRequest.cache, "no-store");
    assert.equal(capturedRequest.credentials, "same-origin");
  } finally { globalThis.fetch = original; }
});

test("shared client exposes creation and replay metadata", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = (async () => new Response(JSON.stringify({ id: 7 }), { status: 201, headers: { ETag: '"v7"', Location: "/api/items/7", "Idempotency-Replayed": "true" } })) as typeof fetch;
  try {
    assert.deepEqual(await requestWithMetadata<{ id: number }>("/api/items"), { body: { id: 7 }, etag: '"v7"', location: "/api/items/7", replayed: true });
  } finally { globalThis.fetch = original; }
});

test("void requests accept every successful empty response without parsing", async () => {
  const original = globalThis.fetch;
  let parsed = false;
  globalThis.fetch = (async () => {
    const response = new Response(null, { status: 204 });
    Object.defineProperty(response, "text", { value: async () => { parsed = true; return ""; } });
    return response;
  }) as typeof fetch;
  try { await requestVoid("/api/items/7", { method: "DELETE" }); assert.equal(parsed, false); } finally { globalThis.fetch = original; }
});

test("client preserves valid API errors and normalizes malformed error bodies", async () => {
  const original = globalThis.fetch;
  const valid = { code: "VALIDATION_FAILED", message: "입력을 확인하세요.", fieldErrors: [{ field: "name", message: "필수입니다." }] };
  globalThis.fetch = (async () => new Response(JSON.stringify(valid), { status: 400 })) as typeof fetch;
  try {
    await assert.rejects(requestJson("/api/items", { method: "POST" }), (error: unknown) => error instanceof ApiError && error.status === 400 && error.code === "VALIDATION_FAILED" && error.fieldErrors[0]?.field === "name");
  } finally { globalThis.fetch = original; }

  globalThis.fetch = (async () => new Response("not-json", { status: 502 })) as typeof fetch;
  try { await assert.rejects(requestJson("/api/items"), (error: unknown) => error instanceof ApiError && error.status === 502 && error.code === "INTERNAL_ERROR"); } finally { globalThis.fetch = original; }
});

test("client rejects malformed successful JSON with an API response error", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = (async () => new Response("", { status: 200 })) as typeof fetch;
  try { await assert.rejects(requestJson("http://localhost/api/items"), (error: unknown) => error instanceof ApiError && error.status === 200 && error.code === "INVALID_API_RESPONSE"); } finally { globalThis.fetch = original; }
});
