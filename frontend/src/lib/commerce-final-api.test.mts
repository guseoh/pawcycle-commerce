import assert from "node:assert/strict";
import test from "node:test";
import { commerceFinalApi } from "./commerce-final-api.ts";

test("admin operation uses the provided endpoint once with CSRF", async () => {
  const original = globalThis.fetch; let path = ""; let csrf = "";
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => { path = String(input); csrf = String(init?.headers && new Headers(init.headers).get("X-CSRF-TOKEN")); return new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }); }) as typeof fetch;
  try { await commerceFinalApi.operation("refunds/9/process", "csrf-token"); assert.equal(path, "/api/admin/refunds/9/process"); assert.equal(csrf, "csrf-token"); } finally { globalThis.fetch = original; }
});
