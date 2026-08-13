import assert from "node:assert/strict";
import test from "node:test";
import { commerceFinalApi } from "./commerce-final-api.ts";

test("admin operation uses the provided endpoint once with CSRF", async () => {
  const original = globalThis.fetch;
  let path = "";
  let csrf = "";
  let method = "";
  let calls = 0;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls += 1;
    path = String(input);
    method = String(init?.method ?? "GET");
    csrf = String(init?.headers && new Headers(init.headers).get("X-CSRF-TOKEN"));
    return new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;
  try {
    await commerceFinalApi.operation("refunds/9/process", "csrf-token");
    assert.equal(calls, 1);
    assert.equal(method, "POST");
    assert.equal(path, "/api/admin/refunds/9/process");
    assert.equal(csrf, "csrf-token");
  } finally {
    globalThis.fetch = original;
  }
});

test("admin billing retry uses its explicit recovery endpoint", async () => {
  const original = globalThis.fetch;
  let path = "";
  let method = "";
  let csrf = "";
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    path = String(input);
    method = String(init?.method ?? "GET");
    csrf = String(init?.headers && new Headers(init.headers).get("X-CSRF-TOKEN"));
    return new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;
  try {
    await commerceFinalApi.operation("payments/12/retry-billing", "csrf-token");
    assert.equal(path, "/api/admin/payments/12/retry-billing");
    assert.equal(method, "POST");
    assert.equal(csrf, "csrf-token");
  } finally {
    globalThis.fetch = original;
  }
});
