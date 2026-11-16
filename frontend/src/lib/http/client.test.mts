import assert from "node:assert/strict";
import test from "node:test";
import { ApiError, isApiErrorBody, withRequestReference } from "./api-error.ts";
import { requestJson, requestVoid, requestWithMetadata } from "./client.ts";
import { interactionApi } from "../interaction-api.ts";

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

const errorRequests = [requestJson, requestVoid, requestWithMetadata];

test("all shared client entrypoints preserve two-field Interaction 400 and 404 errors", async () => {
  const original = globalThis.fetch;
  try {
    for (const [status, code] of [[400, "VALIDATION_FAILED"], [404, "PET_NOT_FOUND"], [404, "PRODUCT_NOT_FOUND"]] as const) {
      const body = { code, message: "요청을 확인하세요." };
      globalThis.fetch = (async () => Response.json(body, { status })) as typeof fetch;
      for (const request of errorRequests) {
        await assert.rejects(request("/api/interactions"), (error: unknown) => {
          assert.ok(error instanceof ApiError);
          assert.equal(error.status, status);
          assert.equal(error.code, code);
          assert.equal(error.message, body.message);
          assert.deepEqual(error.fieldErrors, []);
          return true;
        });
      }
    }
  } finally { globalThis.fetch = original; }
});

test("all shared client entrypoints retain three-field validation details", async () => {
  const original = globalThis.fetch;
  const body = { code: "VALIDATION_FAILED", message: "입력을 확인하세요.", fieldErrors: [{ field: "petId", message: "숫자여야 합니다." }] };
  globalThis.fetch = (async () => Response.json(body, { status: 400 })) as typeof fetch;
  try {
    for (const request of errorRequests) {
      await assert.rejects(request("/api/recommendations/products"), (error: unknown) => {
        assert.ok(error instanceof ApiError);
        assert.equal(error.status, 400);
        assert.equal(error.code, body.code);
        assert.equal(error.message, body.message);
        assert.deepEqual(error.fieldErrors, body.fieldErrors);
        return true;
      });
    }
  } finally { globalThis.fetch = original; }
});

test("API error guard accepts omitted details but rejects every present malformed detail", () => {
  const body = { code: "VALIDATION_FAILED", message: "invalid" };
  assert.equal(isApiErrorBody(body), true);
  assert.equal(isApiErrorBody({ ...body, fieldErrors: [] }), true);
  for (const fieldErrors of [undefined, null, "invalid", 1, {}, [null], ["invalid"], [{}], [{ field: 1, message: "invalid" }], [{ field: "petId", message: null }]]) {
    assert.equal(isApiErrorBody({ ...body, fieldErrors }), false);
  }
  for (const invalid of [null, [], {}, { code: 400, message: "invalid" }, { code: "INVALID", message: null }]) {
    assert.equal(isApiErrorBody(invalid), false);
  }
});

test("malformed, empty and non-JSON error bodies remain safe errors with the original HTTP status", async () => {
  const original = globalThis.fetch;
  const malformed = [null, "invalid", {}, { code: 400, message: "invalid" }, { code: "INVALID", message: null },
    ...[null, "invalid", 1, {}, [null], ["invalid"], [{}], [{ field: 1, message: "invalid" }], [{ field: "petId", message: null }]]
      .map((fieldErrors) => ({ code: "VALIDATION_FAILED", message: "invalid", fieldErrors }))];
  try {
    for (const text of ["", "not-json", ...malformed.map((body) => JSON.stringify(body))]) {
      globalThis.fetch = (async () => new Response(text, { status: 422 })) as typeof fetch;
      for (const request of errorRequests) {
        await assert.rejects(request("/api/interactions"), (error: unknown) => error instanceof ApiError
          && error.status === 422 && error.code === "INTERNAL_ERROR" && error.fieldErrors.length === 0);
      }
    }
  } finally { globalThis.fetch = original; }
});

test("Interaction send preserves CSRF and consumes two-field errors and successful 204", async () => {
  const original = globalThis.fetch;
  const events = [{ eventId: "fixture-event", type: "PRODUCT_VIEW" as const, productId: 1, source: "CATALOG" }];
  let captured: RequestInit | undefined;
  let response = Response.json({ code: "PRODUCT_NOT_FOUND", message: "상품이 없습니다." }, { status: 404 });
  globalThis.fetch = (async (_input: string | URL | Request, init?: RequestInit) => {
    captured = init;
    return response;
  }) as typeof fetch;
  try {
    await assert.rejects(interactionApi.send(events, "fixture-csrf"), (error: unknown) => error instanceof ApiError
      && error.status === 404 && error.code === "PRODUCT_NOT_FOUND" && error.fieldErrors.length === 0);
    assert.ok(captured);
    assert.equal(captured.method, "POST");
    assert.equal(new Headers(captured.headers).get("X-CSRF-TOKEN"), "fixture-csrf");
    assert.equal(new Headers(captured.headers).get("Content-Type"), "application/json");
    assert.equal(captured.credentials, "same-origin");
    assert.equal(captured.cache, "no-store");
    assert.deepEqual(JSON.parse(String(captured.body)), { events });
    response = new Response(null, { status: 204 });
    await interactionApi.send(events, "fixture-csrf");
  } finally { globalThis.fetch = original; }
});

test("shared client retains caller authentication and CSRF headers", async () => {
  const original = globalThis.fetch;
  let captured: Headers | undefined;
  globalThis.fetch = (async (_input: string | URL | Request, init?: RequestInit) => {
    captured = new Headers(init?.headers);
    return Response.json({ ok: true });
  }) as typeof fetch;
  try {
    await requestJson("/api/example", { headers: { Authorization: "Bearer fixture", "X-CSRF-TOKEN": "fixture-csrf" } });
    assert.equal(captured?.get("Authorization"), "Bearer fixture");
    assert.equal(captured?.get("X-CSRF-TOKEN"), "fixture-csrf");
    assert.equal(captured?.get("Accept"), "application/json");
  } finally { globalThis.fetch = original; }
});

test("error requestId comes from the body, falling back to the X-Request-ID header", async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = (async () => Response.json(
      { code: "INTERNAL_ERROR", message: "요청을 처리할 수 없습니다.", fieldErrors: [], requestId: "body-id" },
      { status: 500, headers: { "X-Request-ID": "header-id" } },
    )) as typeof fetch;
    await assert.rejects(requestJson("/api/x"), (error: unknown) => error instanceof ApiError && error.requestId === "body-id");

    globalThis.fetch = (async () => Response.json(
      { code: "AUTH_REQUIRED", message: "인증이 필요합니다." },
      { status: 401, headers: { "X-Request-ID": "header-only" } },
    )) as typeof fetch;
    await assert.rejects(requestJson("/api/x"), (error: unknown) => error instanceof ApiError && error.requestId === "header-only");

    globalThis.fetch = (async () => new Response("<html>bad gateway</html>", { status: 502, headers: { "X-Request-ID": "proxy-id" } })) as typeof fetch;
    await assert.rejects(requestVoid("/api/x", { method: "POST" }), (error: unknown) => (
      error instanceof ApiError && error.code === "INTERNAL_ERROR" && error.requestId === "proxy-id"
    ));

    globalThis.fetch = (async () => Response.json({ code: "X", message: "y" }, { status: 500 })) as typeof fetch;
    await assert.rejects(requestJson("/api/x"), (error: unknown) => error instanceof ApiError && error.requestId === null);
  } finally { globalThis.fetch = original; }
});

test("error bodies with a non-string requestId are not trusted as API errors", () => {
  assert.equal(isApiErrorBody({ code: "X", message: "y", requestId: 7 }), false);
  assert.equal(isApiErrorBody({ code: "X", message: "y", requestId: "abc" }), true);
});

test("request reference is added only for server failures", () => {
  const server = new ApiError(503, { code: "PAYMENT_PROVIDER_UNKNOWN", message: "결과를 확인하지 못했습니다.", requestId: "r-1" });
  const client = new ApiError(409, { code: "CONFLICT", message: "상태가 올바르지 않습니다.", requestId: "r-2" });
  const noId = new ApiError(500, { code: "INTERNAL_ERROR", message: "실패" });
  assert.equal(withRequestReference(server.message, server), "결과를 확인하지 못했습니다. (문의 시 요청 ID: r-1)");
  assert.equal(withRequestReference(client.message, client), "상태가 올바르지 않습니다.");
  assert.equal(withRequestReference(noId.message, noId), "실패");
  assert.equal(withRequestReference("네트워크 오류", new Error("x")), "네트워크 오류");
});
