import assert from "node:assert/strict";
import test from "node:test";
import { productApi } from "./api.ts";

test("Product Detail trust API client keeps public and member requests on the approved paths", async () => {
  const original = globalThis.fetch;
  const requests: Array<{ path: string; method: string; body: string }> = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    requests.push({ path: String(input), method: String(init?.method ?? "GET"), body: String(init?.body ?? "") });
    return new Response(JSON.stringify({ reviewId: 3, rating: 5, content: "좋아요", createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" }), { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;
  try {
    await productApi.reviews("9", 1, 20);
    await productApi.myReview("9");
    await productApi.createReview("9", 5, "좋아요", "csrf");
    await productApi.updateReview(3, 4, "더 좋아요", "csrf");
    await productApi.deleteReview(3, "csrf");
    await productApi.questions("9", 0, 20);
    await productApi.createQuestion("9", "문의", "csrf");
    assert.deepEqual(requests.map((request) => [request.method, request.path]), [
      ["GET", "/api/products/9/reviews?page=1&size=20"],
      ["GET", "/api/products/9/reviews/me"],
      ["POST", "/api/products/9/reviews"],
      ["PATCH", "/api/reviews/3"],
      ["DELETE", "/api/reviews/3"],
      ["GET", "/api/products/9/questions?page=0&size=20"],
      ["POST", "/api/products/9/questions"],
    ]);
    assert.equal(requests[2].body, JSON.stringify({ rating: 5, content: "좋아요" }));
    assert.equal(requests[6].body, JSON.stringify({ content: "문의" }));
  } finally {
    globalThis.fetch = original;
  }
});
