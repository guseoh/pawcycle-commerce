import assert from "node:assert/strict";
import test from "node:test";
import { adminCatalogApi as api } from "./admin-catalog-api.ts";
import { ApiError } from "./api.ts";

type Call = { path: string; method: string; csrf: string | null; body: unknown; credentials: RequestCredentials | undefined; cache: RequestCache | undefined };
function capture(response: Response) {
  const original = globalThis.fetch; const calls: Call[] = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    calls.push({ path: String(input), method: init?.method ?? "GET", csrf: new Headers(init?.headers).get("X-CSRF-TOKEN"), body: init?.body ? JSON.parse(String(init.body)) : undefined, credentials: init?.credentials, cache: init?.cache });
    return response.clone();
  }) as typeof fetch;
  return { calls, restore: () => { globalThis.fetch = original; } };
}

test("Admin GET unwraps canonical resource lists without CSRF", async () => {
  const fixture = { brands: [{ brandId: 1, name: "Brand", slug: "brand", logoUrl: null, active: true, displayOrder: 0 }] };
  const mock = capture(Response.json(fixture));
  try {
    assert.deepEqual(await api.brands.list(), fixture.brands);
    assert.deepEqual(mock.calls, [{ path: "/api/admin/brands", method: "GET", csrf: null, body: undefined, credentials: "same-origin", cache: "no-store" }]);
  } finally { mock.restore(); }
});

test("POST and PATCH send JSON with the caller's CSRF token", async () => {
  const mock = capture(Response.json({ brandId: 9 }, { status: 201 }));
  try {
    await api.brands.create({ name: "QA", slug: "qa", logoUrl: null, active: true, displayOrder: 0 }, "create-token");
    await api.products.patch(7, { status: "PUBLIC", thumbnailUrl: null }, "patch-token");
    assert.deepEqual(mock.calls.map((c) => [c.path, c.method, c.csrf, c.body]), [
      ["/api/admin/brands", "POST", "create-token", { name: "QA", slug: "qa", logoUrl: null, active: true, displayOrder: 0 }],
      ["/api/admin/products/7", "PATCH", "patch-token", { status: "PUBLIC", thumbnailUrl: null }],
    ]);
  } finally { mock.restore(); }
});

test("DELETE sends CSRF with no invented request body", async () => {
  const mock = capture(new Response(null, { status: 200 }));
  try {
    await api.images(3).remove(8, "delete-token");
    assert.deepEqual(mock.calls[0], { path: "/api/admin/products/3/images/8", method: "DELETE", csrf: "delete-token", body: undefined, credentials: "same-origin", cache: "no-store" });
  } finally { mock.restore(); }
});

test("PUT option and Facet assignments use only backend contract fields", async () => {
  const mock = capture(Response.json({}));
  try {
    await api.skus(3).assignOptions(4, [11, 12], "csrf");
    await api.assignProductFacets(3, [21], "csrf");
    await api.assignCategoryFacet(5, 6, 0, "csrf");
    assert.deepEqual(mock.calls.map((c) => [c.path, c.method, c.body]), [
      ["/api/admin/products/3/skus/4/option-values", "PUT", { optionValueIds: [11, 12] }],
      ["/api/admin/products/3/facet-values", "PUT", { facetOptionIds: [21] }],
      ["/api/admin/categories/5/facets/6", "PUT", { displayOrder: 0 }],
    ]);
  } finally { mock.restore(); }
});

test("Admin errors preserve 401, 403, CSRF and fieldErrors", async () => {
  for (const [status, code] of [[401, "AUTH_REQUIRED"], [403, "ACCESS_DENIED"], [403, "CSRF_INVALID"], [400, "VALIDATION_FAILED"]] as const) {
    const mock = capture(Response.json({ code, message: "요청 실패", fieldErrors: [{ field: "name", message: "필수 입력입니다." }] }, { status }));
    try {
      await assert.rejects(api.categories.list(), (error: unknown) => error instanceof ApiError && error.status === status && error.code === code && error.fieldErrors[0]?.field === "name");
    } finally { mock.restore(); }
  }
});

test("malformed success and failure bodies become stable API errors", async () => {
  for (const response of [new Response("not-json", { status: 200 }), Response.json({ nope: true }, { status: 500 })]) {
    const mock = capture(response);
    try { await assert.rejects(api.products.list(), (error: unknown) => error instanceof ApiError && ["INVALID_API_RESPONSE", "INTERNAL_ERROR"].includes(error.code)); }
    finally { mock.restore(); }
  }
});
